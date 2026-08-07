package com.api.bizplay_conversational.service.planPickerAgentService;

import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Same ladder as the purpose/segment agent: deterministic matching resolves chips, ids and
 * verbatim titles without an LLM call; the LLM only ranks genuinely descriptive references
 * ("the education trip last week"); anything still unclear returns null so the orchestrator
 * re-presents the chips — the model can never invent a plan.
 */
@Slf4j
@Service
public class PlanPickerAgentServiceImple implements PlanPickerAgentService {

    private static final String SYSTEM_PROMPT = """
            You match a user's reference to ONE business-trip plan from a numbered candidate list.
            Candidates are the user's own recent trip plans (titles are often Korean).
            Return ONLY JSON, no prose, no markdown: {"best": <candidate number or null>}
            Rules:
            - "best" only when the message clearly refers to exactly one candidate (by topic,
              destination, date, or document number). If several match or none match, best=null.
            - Numbers must come from the list. Never invent one.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;
    private final com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService;

    @Value("${app.conversational.plan-picker-agent.model:qwen3-14b}")
    private String modelName;

    public PlanPickerAgentServiceImple(Map<String, ChatClient> chatClientRegistry,
                                       LlmSettingsService llmSettingsService,
                                       ObjectMapper objectMapper,
                                       com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
        this.agentPromptService = agentPromptService;
        agentPromptService.registerDefault("plan-picker", SYSTEM_PROMPT);
    }

    @Override
    public Long pick(String message, JsonNode candidates) {
        if (message == null || message.isBlank() || candidates == null || !candidates.isArray()
                || candidates.isEmpty()) {
            return null;
        }
        // --- Deterministic fast path -------------------------------------------------
        String lower = message.toLowerCase(Locale.ROOT);
        List<JsonNode> hits = new ArrayList<>();
        for (JsonNode c : candidates) {
            String docNo = c.path("docNo").asText("").toLowerCase(Locale.ROOT);
            String approvalId = c.path("approvalId").asText("");
            String title = c.path("title").asText("").toLowerCase(Locale.ROOT);
            if ((!docNo.isBlank() && lower.contains(docNo))
                    || (!approvalId.isBlank() && lower.contains(approvalId))
                    || (!title.isBlank() && (lower.contains(title) || title.contains(lower.trim())))) {
                hits.add(c);
            }
        }
        if (hits.size() == 1) {
            return hits.get(0).path("approvalId").asLong();
        }
        if (hits.size() > 1) {
            return null;   // several plans share the phrasing — a human must pick
        }
        // --- LLM ranking on the miss --------------------------------------------------
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            log.warn("Plan picker model is not configured: {}", modelName);
            return null;
        }
        try {
            StringBuilder list = new StringBuilder("Candidates:\n");
            int i = 1;
            for (JsonNode c : candidates) {
                list.append(i++).append(". ").append(c.path("docNo").asText(""))
                        .append(" | ").append(c.path("title").asText(""))
                        .append(" | ").append(c.path("startDate").asText(""))
                        .append("~").append(c.path("endDate").asText(""))
                        .append(" | ").append(c.path("purpose").asText("")).append('\n');
            }
            List<Message> prompt = List.of(
                    new SystemMessage(agentPromptService.resolve("plan-picker", SYSTEM_PROMPT)),
                    new UserMessage(list + "\nUser message:\n" + message));
            String raw = client.prompt().messages(prompt).call().content();
            log.info("Plan picker raw output: {}", raw);
            JsonNode parsed = objectMapper.readTree(extractJson(stripThink(raw)));
            int best = parsed.path("best").asInt(0);
            if (best >= 1 && best <= candidates.size()) {
                return candidates.get(best - 1).path("approvalId").asLong();
            }
        } catch (Exception e) {
            log.warn("Plan picking failed: {}", e.getMessage());
        }
        return null;
    }

    private String stripThink(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        return cleaned.replaceAll("(?is)</?think>", "").trim();
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        return (start < 0 || end < start) ? "{}" : cleaned.substring(start, end + 1);
    }
}
