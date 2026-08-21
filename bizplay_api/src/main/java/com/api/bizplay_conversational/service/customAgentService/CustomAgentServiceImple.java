package com.api.bizplay_conversational.service.customAgentService;

import com.api.bizplay_compliance.service.corpService.LocationGeocodeService;
import com.api.bizplay_conversational.model.entity.ConversationalCustomAgent;
import com.api.bizplay_conversational.model.request.CustomAgentRequest;
import com.api.bizplay_conversational.model.response.CustomAgentResponse;
import com.api.bizplay_conversational.model.response.DatabaseLookupAgentResponse;
import com.api.bizplay_conversational.repository.ConversationalCustomAgentRepo;
import com.api.bizplay_conversational.service.databaseLookupAgentService.DatabaseLookupAgentService;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import com.api.bizplay_conversational.service.mcpService.McpClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final int MAX_TOOL_ROUNDS = 6;

    private final ConversationalCustomAgentRepo agentRepo;
    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final DatabaseLookupAgentService databaseLookupAgentService;
    private final McpClientService mcpClientService;
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
            if (t.startsWith("mcp:")) {
                String[] parts = t.split(":", 3);
                if (parts.length != 3 || mcpClientService.list(corpNo).stream()
                        .noneMatch(sv -> sv.getName().equals(parts[1]))) {
                    throw new IllegalArgumentException("Unknown MCP tool '" + t + "' — register the server first.");
                }
                continue;
            }
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
                ONLY when the message clearly matches its "when to use" purpose.
                The MAIN assistant (not listed) already handles everything about business-trip
                plans: creating, editing and saving them, trip purposes, destinations, departure
                places, dates, travelers and forms. A message that DESCRIBES a trip — even as a
                bare noun phrase ("trip to Busan next Monday, departing from X", "부산 출장 다음 주
                월요일") — belongs to the MAIN assistant: return null.
                Route to a custom assistant only when the message is clearly OUTSIDE the trip-plan
                flow AND clearly matches that assistant. When unsure, return null.
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
                .map(String::trim)
                .filter(t -> !t.isBlank() && (TOOLS.containsKey(t) || t.startsWith("mcp:")))
                .toList();
        // Live MCP tool descriptions (name + description + schema) for the prompt.
        List<McpClientService.McpTool> mcpTools = allowed.stream().anyMatch(t -> t.startsWith("mcp:"))
                ? mcpClientService.listTools(corpNo) : List.of();

        StringBuilder system = new StringBuilder(a.getPrompt()).append("\n\n");
        if (!allowed.isEmpty()) {
            system.append("You can use these READ-ONLY tools by responding with ONLY a JSON object:\n");
            for (String t : allowed) {
                if (t.startsWith("mcp:")) {
                    String[] parts = t.split(":", 3);
                    McpClientService.McpTool mt = mcpTools.stream()
                            .filter(x -> x.server().equals(parts[1]) && x.name().equals(parts.length > 2 ? parts[2] : ""))
                            .findFirst().orElse(null);
                    system.append("- ").append(t).append(": ")
                            .append(mt != null ? mt.description() : "external MCP tool");
                    String sig = mt != null ? compactSchema(mt.inputSchemaJson()) : null;
                    if (sig != null) {
                        system.append(" — args ").append(sig).append(" — include EVERY required arg.");
                    }
                    system.append('\n');
                } else {
                    system.append("- ").append(t).append(": ").append(TOOLS.get(t)).append('\n');
                }
            }
            system.append("""
                    Tool call format: {"tool": "<name>", "args": {...}} — ALWAYS include the
                    "tool" key; never output the arguments object alone.
                    If answering needs tool data, your response MUST BE the tool-call JSON itself —
                    never describe or announce what you plan to do.
                    When you have what you need (or need no tool), answer the user with:
                    {"answer": "<final answer, in the user's language>"}
                    One JSON object per response. Never invent tool results.
                    Copy facts from tool results EXACTLY as returned — never translate, romanize,
                    round or adjust names, numbers or addresses.
                    """);
        } else {
            system.append("Answer the user directly with ONLY: {\"answer\": \"<final answer, in the user's language>\"}\n");
        }
        // "The user's language" is too easy to drift on — pin it from the message itself.
        // Proportion, not presence: an English sentence quoting one Korean name (김철수)
        // must still count as English.
        long hangul = message.codePoints().filter(cp ->
                (cp >= 0xAC00 && cp <= 0xD7A3) || (cp >= 0x1100 && cp <= 0x11FF) || (cp >= 0x3130 && cp <= 0x318F)).count();
        long latin = message.codePoints().filter(Character::isAlphabetic).count() - hangul;
        // The site's ENG/KOR switch wins when the chat sent its marker — otherwise a Korean
        // user typing an English word gets an English answer from a custom agent.
        boolean korean = message.contains("Respond in Korean only")
                || (!message.contains("Respond in English only") && hangul > 0 && hangul * 3 > latin);
        system.append(korean
                ? "The user wrote in Korean — the \"answer\" text MUST be in Korean only.\n"
                : "The user wrote in English — the \"answer\" text MUST be in English only.\n");
        system.append("/no_think");

        List<Message> convo = new ArrayList<>();
        convo.add(new SystemMessage(system.toString()));
        convo.add(new UserMessage(message));
        boolean nudged = false;
        Set<String> executedCalls = new HashSet<>();

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
                // Plan-narration guard: an "answer" with tools available but none used yet gets
                // ONE nudge — either call the tool now or restate the final answer.
                if (!nudged && executedCalls.isEmpty() && !allowed.isEmpty()) {
                    nudged = true;
                    convo.add(new AssistantMessage(raw));
                    convo.add(new UserMessage(
                            "You answered without using any tool. If the question needs tool data, "
                            + "respond NOW with the tool-call JSON ({\"tool\": ...}). If no tool is "
                            + "truly needed, restate the final answer as {\"answer\": ...}."));
                    continue;
                }
                return new RoutedReply(a.getName(), parsed.path("answer").asText(), toolsUsed);
            }
            String tool = parsed.path("tool").asText(null);
            // Tolerance: some models emit the ARGUMENTS object without the {"tool":..} wrapper.
            // With exactly one allowed tool that's unambiguous — treat it as that tool's args.
            if (tool == null && allowed.size() == 1 && parsed.isObject() && parsed.size() > 0) {
                tool = allowed.get(0);
                parsed = objectMapper.createObjectNode().set("args", parsed);
            }
            if (tool == null) {
                return new RoutedReply(a.getName(),
                        parsed.path("answer").asText(stripThink(raw).trim()), toolsUsed);
            }
            if (!allowed.contains(tool)) {
                // Models (and stale agent prompts) invent tool names — correct, don't leak JSON.
                if (round == MAX_TOOL_ROUNDS) {
                    break;
                }
                convo.add(new AssistantMessage(raw));
                convo.add(new UserMessage("\"" + tool + "\" is not an available tool. Your ONLY tools are: "
                        + String.join(", ", allowed)
                        + ". Call one of those, or answer with {\"answer\": \"...\"}."));
                continue;
            }
            if (round == MAX_TOOL_ROUNDS) {
                break;      // tool budget exhausted mid-call — force a final answer below
            }
            String argsJson = parsed.path("args").toString();
            String callKey = tool + "|" + argsJson;
            convo.add(new AssistantMessage(raw));
            if (!executedCalls.add(callKey)) {
                convo.add(new UserMessage("You already called " + tool + " with those exact arguments — "
                        + "the result is above. Do not repeat it. Call the NEXT tool you need, "
                        + "or answer with {\"answer\": \"...\"}."));
                continue;
            }
            String result;
            if (tool.startsWith("mcp:")) {
                String[] parts = tool.split(":", 3);
                result = mcpClientService.callTool(corpNo, parts[1], parts.length > 2 ? parts[2] : "",
                        argsJson);
            } else {
                String query = parsed.path("args").path("query").asText(argsJson);
                result = executeTool(tool, corpNo, query);
            }
            if (result != null && (result.startsWith("TOOL BLOCKED:") || result.startsWith("TOOL FAILED:"))) {
                convo.add(new UserMessage("TOOL RESULT (" + tool + "):\n" + result
                        + "\nThe tool could NOT run. Answer with {\"answer\": \"...\"} telling the user "
                        + "it couldn't run and why — do NOT answer the question from your own knowledge."));
                continue;
            }
            toolsUsed.add(tool);
            convo.add(new UserMessage("TOOL RESULT (" + tool + "):\n" + result
                    + "\nNow either call another tool or answer with {\"answer\": \"...\"}. "
                    + "If this result was empty or unhelpful, do NOT rephrase the same lookup again — "
                    + "move on to your other tools (if any part of the question is still unanswered) "
                    + "or answer with what you have."));
        }
        // Ran out of tool rounds — one last call that only accepts an answer.
        convo.add(new UserMessage("No more tool calls are allowed. Answer the user's question NOW "
                + "with {\"answer\": \"...\"} using the tool results above."));
        String raw = callLlm(client, fallback, convo);
        if (raw != null) {
            try {
                JsonNode fin = objectMapper.readTree(extractJson(raw));
                return new RoutedReply(a.getName(), fin.path("answer").asText(stripThink(raw).trim()), toolsUsed);
            } catch (Exception e) {
                return new RoutedReply(a.getName(), stripThink(raw).trim(), toolsUsed);
            }
        }
        return new RoutedReply(a.getName(), "Sorry — I couldn't finish that.", toolsUsed);
    }

    /** Boil an MCP inputSchema down to "{name: type (REQUIRED), ...}" — full schemas with long
     *  per-property descriptions blow past any sane prompt budget, but the model must still see
     *  every argument name and which ones are required. */
    private String compactSchema(String schemaJson) {
        if (schemaJson == null) {
            return null;
        }
        try {
            JsonNode s = objectMapper.readTree(schemaJson);
            Set<String> required = new HashSet<>();
            s.path("required").forEach(n -> required.add(n.asText()));
            List<String> parts = new ArrayList<>();
            s.path("properties").fields().forEachRemaining(e -> parts.add(
                    e.getKey() + ": " + e.getValue().path("type").asText("any")
                    + (required.contains(e.getKey()) ? " (REQUIRED)" : " (optional)")));
            return parts.isEmpty() ? null : "{" + String.join(", ", parts) + "}";
        } catch (Exception e) {
            return null;
        }
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
