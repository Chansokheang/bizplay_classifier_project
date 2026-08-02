package com.api.bizplay_conversational.service.customAgentService;

import com.api.bizplay_compliance.service.corpService.LocationGeocodeService;
import com.api.bizplay_conversational.model.entity.ConversationalCustomAgent;
import com.api.bizplay_conversational.model.request.CustomAgentRequest;
import com.api.bizplay_conversational.model.response.CustomAgentResponse;
import com.api.bizplay_conversational.model.response.DatabaseLookupAgentResponse;
import com.api.bizplay_conversational.repository.ConversationalCustomAgentRepo;
import com.api.bizplay_conversational.service.databaseLookupAgentService.DatabaseLookupAgentService;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomAgentServiceImple implements CustomAgentService {

    /** Phase-1 built-in tools — READ-ONLY by construction. */
    private static final Map<String, String> TOOLS = new LinkedHashMap<>() {{
        put("db-select", "Query the company database (staff, departments, past trip plans/reports) "
                + "with a natural-language question — read-only, returns rows.");
        put("geocode", "Verify/normalize a Korean ADDRESS (도로명/지번) via the Naver geocoder — returns the "
                + "official address. Works on addresses only, NOT on place/station/building names.");
    }};

    private static final int MAX_TOOL_ROUNDS = 3;

    private final ConversationalCustomAgentRepo agentRepo;
    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final DatabaseLookupAgentService databaseLookupAgentService;
    private final LocationGeocodeService locationGeocodeService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.custom-agent.model:qwen3-14b}")
    private String defaultModelName;

    @PostConstruct
    void init() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS conversational_custom_agent (
                        corp_no      VARCHAR(50)  NOT NULL,
                        name         VARCHAR(100) NOT NULL,
                        description  TEXT NOT NULL,
                        prompt       TEXT NOT NULL,
                        model        VARCHAR(100),
                        tools        VARCHAR(500) NOT NULL DEFAULT '',
                        enabled      BOOLEAN NOT NULL DEFAULT TRUE,
                        created_date TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (corp_no, name)
                    )""");
        } catch (Exception e) {
            log.warn("Could not bootstrap conversational_custom_agent ({}).", e.getMessage());
        }
    }

    // --- CRUD ---------------------------------------------------------------------

    @Override
    public List<CustomAgentResponse> list(String corpNo) {
        requireCorp(corpNo);
        List<CustomAgentResponse> out = new ArrayList<>();
        for (ConversationalCustomAgent a : agentRepo.findAll(corpNo.trim())) {
            out.add(toResponse(a));
        }
        return out;
    }

    @Override
    public CustomAgentResponse put(String corpNo, String name, CustomAgentRequest request) {
        requireCorp(corpNo);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required.");
        }
        if (request == null || request.getDescription() == null || request.getDescription().isBlank()) {
            throw new IllegalArgumentException("description (\"when to use\") is required — the router matches on it.");
        }
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("prompt is required.");
        }
        List<String> tools = request.getTools() == null ? List.of() : request.getTools();
        for (String t : tools) {
            if (!TOOLS.containsKey(t)) {
                throw new IllegalArgumentException("Unknown tool '" + t + "'. Allowed: " + TOOLS.keySet());
            }
        }
        ConversationalCustomAgent a = new ConversationalCustomAgent();
        a.setCorpNo(corpNo.trim());
        a.setName(name.trim());
        a.setDescription(request.getDescription().trim());
        a.setPrompt(request.getPrompt().trim());
        a.setModel(null);   // agents run on the model selected in Chat (active model)
        a.setTools(String.join(",", tools));
        a.setEnabled(request.getEnabled() == null || request.getEnabled());
        agentRepo.upsert(a);
        log.info("Custom agent '{}' saved for corp {} (tools={}, enabled={}).", name, corpNo, tools, a.isEnabled());
        return toResponse(agentRepo.findByName(corpNo.trim(), name.trim()));
    }

    @Override
    public void delete(String corpNo, String name) {
        requireCorp(corpNo);
        agentRepo.deleteByName(corpNo.trim(), name);
        log.info("Custom agent '{}' deleted for corp {}.", name, corpNo);
    }

    @Override
    public Map<String, String> toolCatalog() {
        return TOOLS;
    }

    // --- execution ---------------------------------------------------------------

    @Override
    public CustomAgentResponse test(String corpNo, String name, String message) {
        requireCorp(corpNo);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }
        ConversationalCustomAgent a = agentRepo.findByName(corpNo.trim(), name);
        if (a == null) {
            throw new IllegalArgumentException("Unknown custom agent: '" + name + "'.");
        }
        RoutedReply r = run(a, corpNo.trim(), message.trim());
        return CustomAgentResponse.builder()
                .name(a.getName()).reply(r.reply()).toolsUsed(r.toolsUsed()).build();
    }

    @Override
    public RoutedReply tryHandle(String corpNo, String message) {
        if (corpNo == null || corpNo.isBlank() || message == null || message.isBlank()) {
            return null;
        }
        List<ConversationalCustomAgent> agents;
        try {
            agents = agentRepo.findAll(corpNo.trim()).stream().filter(ConversationalCustomAgent::isEnabled).toList();
        } catch (Exception e) {
            return null;
        }
        if (agents.isEmpty()) {
            return null;
        }
        ConversationalCustomAgent picked = route(agents, message);
        if (picked == null) {
            return null;
        }
        log.info("Custom agent '{}' claimed the turn for corp {}.", picked.getName(), corpNo);
        return run(picked, corpNo.trim(), message);
    }

    /** LLM router: pick at most one agent by its "when to use" description. */
    private ConversationalCustomAgent route(List<ConversationalCustomAgent> agents, String message) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(defaultModelName));
        if (client == null) {
            return null;
        }
        StringBuilder catalog = new StringBuilder();
        for (ConversationalCustomAgent a : agents) {
            catalog.append("- ").append(a.getName()).append(": ").append(a.getDescription().replace('\n', ' ')).append('\n');
        }
        String system = """
                You route a user's chat message to at most ONE custom assistant. Pick an assistant
                ONLY when the message clearly matches its "when to use" purpose. Requests to create,
                edit or save a business-trip plan are NOT for custom assistants — return null.
                Return ONLY JSON: {"agent": "<assistant name>" | null}
                /no_think
                """;
        try {
            String raw = callLlm(client, chatClientRegistry.get(defaultModelName), List.of(
                    new SystemMessage(system),
                    new UserMessage("Assistants:\n" + catalog + "\nUser message:\n" + message)));
            if (raw == null) {
                return null;
            }
            JsonNode parsed = objectMapper.readTree(extractJson(raw));
            String name = parsed.path("agent").asText(null);
            if (name == null || name.isBlank() || "null".equalsIgnoreCase(name)) {
                return null;
            }
            return agents.stream().filter(a -> a.getName().equalsIgnoreCase(name.trim())).findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("Custom-agent routing failed ({}) — normal flow continues.", e.getMessage());
            return null;
        }
    }

    /**
     * A blank-safe LLM call: reasoning-style models (e.g. LUXIA) sometimes return an EMPTY
     * content with everything in a reasoning field — retry once, then fall back to the agent's
     * configured model without the active-model override.
     */
    private String callLlm(ChatClient primary, ChatClient fallback, List<Message> convo) {
        ChatClient[] attempts = (fallback != null && fallback != primary)
                ? new ChatClient[]{primary, primary, fallback}
                : new ChatClient[]{primary, primary};
        for (ChatClient c : attempts) {
            try {
                String raw = c.prompt().messages(convo).call().content();
                if (raw != null && !stripThink(raw).isBlank()) {
                    return raw;
                }
                log.warn("Custom-agent LLM returned blank content — retrying{}.",
                        c == attempts[attempts.length - 1] ? " exhausted" : "");
            } catch (Exception e) {
                log.warn("Custom-agent LLM call failed: {}", e.getMessage());
            }
        }
        return null;
    }

    /** Bounded tool-calling loop: the model either calls a tool (JSON) or answers. */
    private RoutedReply run(ConversationalCustomAgent a, String corpNo, String message) {
        // One model source of truth: the model selected in Chat (the active override),
        // falling back to the configured default when the reply comes back blank.
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(defaultModelName));
        ChatClient fallback = chatClientRegistry.get(defaultModelName);
        List<String> toolsUsed = new ArrayList<>();
        if (client == null) {
            client = fallback;
        }
        if (client == null) {
            return new RoutedReply(a.getName(), "This assistant's model is unavailable right now.", toolsUsed);
        }
        List<String> allowed = Arrays.stream(a.getTools().split(","))
                .map(String::trim).filter(t -> !t.isBlank() && TOOLS.containsKey(t)).toList();

        StringBuilder system = new StringBuilder(a.getPrompt()).append("\n\n");
        if (!allowed.isEmpty()) {
            system.append("You can use these READ-ONLY tools by responding with ONLY a JSON object:\n");
            for (String t : allowed) {
                system.append("- ").append(t).append(": ").append(TOOLS.get(t)).append('\n');
            }
            system.append("""
                    Tool call format: {"tool": "<name>", "args": {"query": "<what you need>"}}
                    When you have what you need (or need no tool), answer the user with:
                    {"answer": "<final answer, in the user's language>"}
                    One JSON object per response. Never invent tool results.
                    Copy facts from tool results EXACTLY as returned — never translate, romanize,
                    round or adjust names, numbers or addresses.
                    """);
        } else {
            system.append("Answer the user directly with ONLY: {\"answer\": \"<final answer, in the user's language>\"}\n");
        }
        system.append("/no_think");

        List<Message> convo = new ArrayList<>();
        convo.add(new SystemMessage(system.toString()));
        convo.add(new UserMessage(message));

        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            String raw = callLlm(client, fallback, convo);
            if (raw == null) {
                return new RoutedReply(a.getName(), "Sorry — I couldn't process that just now.", toolsUsed);
            }
            String cleaned = extractJson(raw);
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(cleaned);
            } catch (Exception e) {
                // Not JSON — treat the raw text as the answer rather than failing the turn.
                return new RoutedReply(a.getName(), stripThink(raw).trim(), toolsUsed);
            }
            if (parsed.hasNonNull("answer")) {
                return new RoutedReply(a.getName(), parsed.path("answer").asText(), toolsUsed);
            }
            String tool = parsed.path("tool").asText(null);
            if (tool == null || !allowed.contains(tool) || round == MAX_TOOL_ROUNDS) {
                return new RoutedReply(a.getName(),
                        parsed.path("answer").asText(stripThink(raw).trim()), toolsUsed);
            }
            String query = parsed.path("args").path("query").asText(parsed.path("args").toString());
            String result = executeTool(tool, corpNo, query);
            toolsUsed.add(tool);
            convo.add(new UserMessage("TOOL RESULT (" + tool + "):\n" + result
                    + "\nNow either call another tool or answer with {\"answer\": \"...\"}."));
        }
        return new RoutedReply(a.getName(), "Sorry — I couldn't finish that.", toolsUsed);
    }

    private String executeTool(String tool, String corpNo, String query) {
        try {
            switch (tool) {
                case "db-select" -> {
                    DatabaseLookupAgentResponse res = databaseLookupAgentService.lookup(corpNo, query);
                    if (res == null || !res.isExecuted() || res.getError() != null) {
                        return "lookup failed" + (res != null && res.getError() != null ? ": " + res.getError() : "");
                    }
                    List<Map<String, Object>> rows = res.getRows() == null ? List.of() : res.getRows();
                    return rows.isEmpty() ? "no rows found"
                            : objectMapper.writeValueAsString(rows.subList(0, Math.min(rows.size(), 15)));
                }
                case "geocode" -> {
                    LocationGeocodeService.GeocodedLocation loc = locationGeocodeService.geocode(query);
                    String road = loc.roadAddress() != null ? loc.roadAddress() : loc.jibunAddress();
                    return road == null ? "no result" : road;
                }
                default -> {
                    return "unknown tool";
                }
            }
        } catch (Exception e) {
            return "tool error: " + e.getMessage();
        }
    }

    // --- helpers -----------------------------------------------------------------

    private CustomAgentResponse toResponse(ConversationalCustomAgent a) {
        return CustomAgentResponse.builder()
                .name(a.getName())
                .description(a.getDescription())
                .prompt(a.getPrompt())
                .model(a.getModel())
                .tools(a.getTools() == null || a.getTools().isBlank()
                        ? List.of() : Arrays.stream(a.getTools().split(",")).map(String::trim).toList())
                .enabled(a.isEnabled())
                .updatedDate(a.getUpdatedDate())
                .build();
    }

    private static void requireCorp(String corpNo) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
    }

    private static String stripThink(String value) {
        return value == null ? "" : value.replaceAll("(?is)<think>.*?</think>", "").trim();
    }

    private static String extractJson(String text) {
        String cleaned = stripThink(text).replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        return (start < 0 || end < start) ? "{}" : cleaned.substring(start, end + 1);
    }
}
