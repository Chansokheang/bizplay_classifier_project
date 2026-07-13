package com.api.bizplay_conversational.service.updateAgentService;

import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.DraftEditPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
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
public class UpdateAgentServiceImple implements UpdateAgentService {

    private static final String SYSTEM_PROMPT = """
            You convert a user's EDIT request into a JSON list of operations on an EXISTING business-trip draft.
            Return ONLY JSON, no prose, no markdown, no code fences:
            { "edits": [ { "op": ..., "field": ..., "traveler": ..., "value": ... } ] }

            Allowed "op" values:
            - "set_trip_field"          : set a trip-level field      (use field + value)
            - "clear_trip_field"        : clear a trip-level field     (use field)
            - "set_traveler_field"      : set a field for ONE traveler (use traveler + field + value)
            - "clear_traveler_field"    : clear a field for ONE traveler (use traveler + field)
            - "set_all_travelers_field" : set a field for EVERY traveler (use field + value)
            - "add_traveler"            : add a traveler by name        (use traveler)
            - "remove_traveler"         : remove a traveler by name     (use traveler)

            Allowed trip "field": "destination","type","title","start_date","end_date","period","content".
            Allowed traveler "field": "origin","destination","return_point","transportation".
            For "type", value MUST be one of: "General business trip","Educational business trip","Overseas business trip".
            Dates use YYYY-MM-DD.

            Rules:
            - Emit ONLY operations the user explicitly asked for. Never invent changes.
            - Use the EXACT traveler names from the current draft when targeting a person.
            - "everyone"/"all travelers" -> set_all_travelers_field. A single named person -> set_traveler_field.
            - "destination" is a GEOGRAPHIC PLACE only (a city, country, or region, e.g. "Cambodia",
              "Seoul", "Toronto"). NEVER put the trip's reason or purpose (e.g. "meet client",
              "attend conference", "client meeting", "training") into destination. If the user only
              states a reason and no place, do NOT set destination.
            - In a phrase like "from X to Y", X is the origin and Y is the destination (a place).
            - If nothing is a valid edit, return { "edits": [] }.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.update-agent.model:qwen3-14b}")
    private String modelName;

    public UpdateAgentServiceImple(Map<String, ChatClient> chatClientRegistry, ObjectMapper objectMapper,
                                   LlmSettingsService llmSettingsService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DraftEditPlan plan(String message, TripPlanDraft currentDraft, List<Message> history) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            log.warn("Update agent model is not configured: {}", modelName);
            return new DraftEditPlan();
        }
        try {
            String draftJson;
            try {
                draftJson = objectMapper.writeValueAsString(currentDraft);
            } catch (Exception e) {
                draftJson = "{}";
            }

            List<Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(SYSTEM_PROMPT));
            if (history != null) {
                prompt.addAll(history);
            }
            prompt.add(new UserMessage("Current draft:\n" + draftJson + "\n\nEdit request:\n" + message));

            String content = client.prompt().messages(prompt).call().content();
            log.info("Update agent raw output={}", content);

            String json = extractJson(stripThink(content));
            if (json == null) {
                return new DraftEditPlan();
            }
            DraftEditPlan plan = objectMapper.readValue(json, DraftEditPlan.class);
            return plan != null ? plan : new DraftEditPlan();
        } catch (Exception e) {
            log.warn("Update agent failed, returning no edits: {}", e.getMessage());
            return new DraftEditPlan();
        }
    }

    /** Remove reasoning blocks emitted by thinking models (e.g. Qwen3 &lt;think&gt;...&lt;/think&gt;). */
    private String stripThink(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)</?think>", "");
        return cleaned.trim();
    }

    /** Extract the first JSON object from the model output, tolerating stray text or fences. */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }
}
