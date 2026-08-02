package com.api.bizplay_conversational.service.purposeSegmentAgentService;

import com.api.bizplay_conversational.model.response.PurposeOption;
import com.api.bizplay_conversational.model.response.PurposeResolutionResult;
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
import java.util.Map;

@Slf4j
@Service
public class PurposeSegmentAgentServiceImple implements PurposeSegmentAgentService {

    private static final String SYSTEM_PROMPT = """
            You match a user's business-trip request to ONE option from a numbered catalog of
            Travel Purpose x Trip Type options. The catalog is corp-specific configuration; option
            names may be Korean (해외출장 = overseas trip, 국내출장 = domestic trip, 장기 = long-term,
            일반 = general, 교육 = training).
            Return ONLY JSON, no prose, no markdown:
            {"best": <option number or null>, "alternatives": [<option numbers>], "reason": "..."}
            Rules:
            - "best" only when the message clearly implies one option (e.g. an overseas destination
              -> an overseas purpose). Otherwise best=null and list plausible options in "alternatives".
            - If the message says nothing about the trip type, best=null and alternatives=[] (the user
              will be shown the full list).
            - Numbers must come from the catalog. Do not invent options.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;
    private final com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService;

    @Value("${app.conversational.purpose-segment-agent.model:qwen3-14b}")
    private String modelName;

    public PurposeSegmentAgentServiceImple(Map<String, ChatClient> chatClientRegistry,
                                           LlmSettingsService llmSettingsService,
                                           ObjectMapper objectMapper,
                                           com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
        this.agentPromptService = agentPromptService;
        agentPromptService.registerDefault("purpose-segment", SYSTEM_PROMPT);
    }

    @Override
    public List<PurposeOption> flattenCatalog(JsonNode catalog) {
        List<PurposeOption> options = new ArrayList<>();
        if (catalog == null || !catalog.isArray()) {
            return options;
        }
        for (JsonNode purpose : catalog) {
            if (!purpose.path("activated").asBoolean(true)) {
                continue;
            }
            long purposeId = purpose.path("id").asLong();
            String purposeName = purpose.path("purpose").asText("").trim();
            JsonNode segments = purpose.path("segments");
            boolean any = false;
            for (JsonNode seg : segments) {
                if (!seg.path("activated").asBoolean(true)) {
                    continue;
                }
                any = true;
                String segmentName = seg.path("segment").asText("").trim();
                options.add(PurposeOption.builder()
                        .purposeId(purposeId)
                        .segmentId(seg.path("id").asLong())
                        .purposeName(purposeName)
                        .segmentName(segmentName)
                        .label(purposeName + " · " + segmentName)
                        .sendText("Trip type: " + purposeName + " / " + segmentName)
                        .build());
            }
            if (!any) {
                // Purpose without segments is still selectable on its own.
                options.add(PurposeOption.builder()
                        .purposeId(purposeId)
                        .purposeName(purposeName)
                        .label(purposeName)
                        .sendText("Trip type: " + purposeName)
                        .build());
            }
        }
        return options;
    }

    @Override
    public PurposeResolutionResult resolve(String message, List<PurposeOption> options) {
        if (options == null || options.isEmpty()) {
            return PurposeResolutionResult.builder()
                    .candidates(List.of()).reason("No purposes configured for this corporation user.").build();
        }
        if (message == null || message.isBlank()) {
            return PurposeResolutionResult.builder()
                    .candidates(options).reason("No message to analyze; showing the full catalog.").build();
        }
        // Deterministic fast path: the chip sendText ("Trip type: <purpose> / <segment>") or an exact
        // label typed back resolves without an LLM call.
        String lower = message.toLowerCase();
        for (PurposeOption o : options) {
            if (lower.contains(o.getSendText().toLowerCase()) || lower.contains(o.getLabel().toLowerCase())) {
                return PurposeResolutionResult.builder().resolved(o).reason("Exact option match.").build();
            }
        }
        // Normalized fast path: users write the option words without the " · " separator
        // ("해외출장 장기로 …"). When the message contains an option's purpose name AND its
        // segment name (or the purpose has no segment), that option wins deterministically —
        // no LLM judgment involved, so a weaker/over-cautious model cannot break these cases.
        // Longest combined name wins, so "테스트(유성린) 성린4" beats the bare "테스트" purpose.
        PurposeOption tokenMatch = null;
        int tokenLen = 0;
        boolean tokenTie = false;
        for (PurposeOption o : options) {
            String p = o.getPurposeName() == null ? "" : o.getPurposeName().toLowerCase();
            String s = o.getSegmentName() == null ? "" : o.getSegmentName().toLowerCase();
            if (p.isEmpty() || !lower.contains(p) || (!s.isEmpty() && !lower.contains(s))) {
                continue;
            }
            int len = p.length() + s.length();
            if (len > tokenLen) {
                tokenMatch = o;
                tokenLen = len;
                tokenTie = false;
            } else if (len == tokenLen) {
                tokenTie = true;
            }
        }
        if (tokenMatch != null && !tokenTie) {
            return PurposeResolutionResult.builder()
                    .resolved(tokenMatch).reason("Purpose and trip-type names found in the message.").build();
        }

        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            return PurposeResolutionResult.builder()
                    .candidates(options).reason("Model unavailable; showing the full catalog.").build();
        }
        try {
            StringBuilder cat = new StringBuilder("Catalog:\n");
            for (int i = 0; i < options.size(); i++) {
                PurposeOption o = options.get(i);
                cat.append(i + 1).append(". ").append(o.getLabel()).append('\n');
            }
            List<Message> prompt = List.of(
                    new SystemMessage(agentPromptService.resolve("purpose-segment", SYSTEM_PROMPT)),
                    new UserMessage(cat + "\nUser message:\n" + message));
            String raw = client.prompt().messages(prompt).call().content();
            JsonNode parsed = objectMapper.readTree(extractJson(stripThink(raw)));

            Integer best = parsed.path("best").isNumber() ? parsed.path("best").asInt() : null;
            String reason = parsed.path("reason").asText(null);
            if (best != null && best >= 1 && best <= options.size()) {
                return PurposeResolutionResult.builder()
                        .resolved(options.get(best - 1)).reason(reason).build();
            }
            List<PurposeOption> candidates = new ArrayList<>();
            for (JsonNode n : parsed.path("alternatives")) {
                int idx = n.asInt();
                if (idx >= 1 && idx <= options.size()) {
                    candidates.add(options.get(idx - 1));
                }
            }
            if (candidates.isEmpty()) {
                candidates = options; // nothing plausible -> let the user pick from everything
            }
            return PurposeResolutionResult.builder().candidates(candidates).reason(reason).build();
        } catch (Exception e) {
            log.warn("Purpose resolution failed ({}); falling back to full catalog.", e.getMessage());
            return PurposeResolutionResult.builder()
                    .candidates(options).reason("Analysis failed; showing the full catalog.").build();
        }
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
