package com.api.bizplay_conversational.service.slotFillerAgentService;

import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Slot-Filler sub-agent impl — one focused task: turn a free-text message into the requested
 * request parameters as JSON. Its rule ({@code settlement-slot-extract}) is its own, separate from
 * every other sub-agent's prompt. Blank-safe and failure-safe: returns {@code {}} rather than
 * throwing, so the orchestrator keeps its deterministic parsing as the reliable base.
 */
@Slf4j
@Service
public class SlotFillerAgentServiceImple implements SlotFillerAgentService {

    private static final String EXTRACT_PROMPT = """
            You extract structured parameters from a user's message for a business-trip settlement
            assistant. Return a SINGLE JSON object containing ONLY the requested fields the user
            clearly states; OMIT any field not present. Never invent values, never add fields that
            were not requested. If nothing is present, return {}.
            Requested fields (name: meaning):
            %1$s
            Rules:
            - Dates: ISO yyyy-MM-dd. Today is %2$s. Resolve relative expressions against it
              ("last month" -> that month's first..last day; "지난달" likewise; a single month ->
              its first..last day). Emit startDate/endDate only when the user actually gave a period.
            - cardTypes: a JSON array drawn from [CORP, PERSONAL, MY_DATA]. corporate/법인 -> CORP,
              personal/개인 -> PERSONAL, mydata/마이데이터 -> MY_DATA, all/전체 -> all three.
            - Free-text hints (e.g. planHint): copy the user's own words, trimmed.
            Output: raw JSON only — no markdown, no code fence, no commentary, no preamble.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final AgentPromptService agentPromptService;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.slot-filler-agent.model:qwen3-14b}")
    private String modelName;

    public SlotFillerAgentServiceImple(Map<String, ChatClient> chatClientRegistry,
                                       LlmSettingsService llmSettingsService,
                                       AgentPromptService agentPromptService,
                                       ObjectMapper objectMapper) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.agentPromptService = agentPromptService;
        this.objectMapper = objectMapper;
        agentPromptService.registerDefault("settlement-slot-extract", EXTRACT_PROMPT);
    }

    @Override
    public JsonNode extract(String message, Map<String, String> wantedSlots, boolean korean) {
        ObjectNode empty = objectMapper.createObjectNode();
        if (message == null || message.isBlank() || wantedSlots == null || wantedSlots.isEmpty()) {
            return empty;
        }
        String fields = wantedSlots.entrySet().stream()
                .map(e -> "- " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
        String today = java.time.LocalDate.now().toString();
        List<Message> prompt = List.of(
                new SystemMessage(agentPromptService.resolve("settlement-slot-extract", EXTRACT_PROMPT)
                        .formatted(fields, today)),
                new UserMessage(message));
        ChatClient primary = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        ChatClient alt = chatClientRegistry.get(modelName);
        ChatClient[] attempts = (alt != null && alt != primary)
                ? new ChatClient[]{primary, alt}
                : new ChatClient[]{primary};
        for (ChatClient client : attempts) {
            if (client == null) {
                continue;
            }
            try {
                String reply = client.prompt().messages(prompt).call().content();
                JsonNode parsed = parseJsonObject(reply);
                if (parsed != null) {
                    // Keep only the requested slot names — the model can echo extras.
                    ObjectNode kept = objectMapper.createObjectNode();
                    for (String name : wantedSlots.keySet()) {
                        if (parsed.hasNonNull(name)) {
                            kept.set(name, parsed.get(name));
                        }
                    }
                    return kept;
                }
            } catch (Exception e) {
                log.warn("Slot extraction failed ({}); the orchestrator falls back to its parsers.", e.getMessage());
            }
        }
        return empty;
    }

    /** First balanced {...} object in the model's reply, parsed; null when there isn't one. */
    private JsonNode parseJsonObject(String reply) {
        if (reply == null) {
            return null;
        }
        String cleaned = reply.replaceAll("(?is)<think>.*?</think>", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }
}
