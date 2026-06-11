package com.api.bizplay_compliance.service.geoService;

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

/**
 * LLM-backed geo normalizer. Reuses the chatbot module's {@code chatClientRegistry} (same wiring as
 * the conversational sub-agents); the model is configurable via
 * {@code app.compliance.geo-normalization.model} and defaults to the receipt agent's Gemma model.
 * Unlike the Naver geocode API (Korea-only), an LLM resolves both Korean and non-Korean places.
 * Failures degrade gracefully to an empty {@link GeoPoint} so the audit never breaks on a bad call.
 */
@Slf4j
@Service
public class GeoNormalizationServiceImple implements GeoNormalizationService {

    private static final String SYSTEM_PROMPT = """
            You normalize ONE place description to its city and country. Return ONLY JSON, no prose, no markdown, no code fences:
            {"city": "...", "country": "..."}
            Rules:
            - Resolve venues, landmarks, airports, stations, or addresses to the city they are located in
              (e.g. "KSHRD center" -> city "Phnom Penh", country "Cambodia";
              "Incheon Intl Airport" or "ICN" -> city "Seoul", country "South Korea";
              "강남역" -> city "Seoul", country "South Korea").
            - Korean place names are common; resolve them as readily as English ones.
            - Use the common English city and country names.
            - If you genuinely cannot determine a field, use null. Do not invent.
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final ObjectMapper objectMapper;

    @Value("${app.compliance.geo-normalization.model:gemma-4-e4b}")
    private String modelName;

    public GeoNormalizationServiceImple(Map<String, ChatClient> chatClientRegistry, ObjectMapper objectMapper) {
        this.chatClientRegistry = chatClientRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeoPoint normalize(String location) {
        if (location == null || location.isBlank()) {
            return new GeoPoint(null, null);
        }
        ChatClient client = chatClientRegistry.get(modelName);
        if (client == null) {
            log.warn("Geo normalization model is not configured: {}", modelName);
            return new GeoPoint(null, null);
        }
        try {
            List<Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(SYSTEM_PROMPT));
            prompt.add(new UserMessage("Place: " + location.trim()));

            String raw = client.prompt().messages(prompt).call().content();
            String json = extractJson(stripThink(raw));
            if (json == null) {
                return new GeoPoint(null, null);
            }
            JsonNode node = objectMapper.readTree(json);
            return new GeoPoint(textOrNull(node, "city"), textOrNull(node, "country"));
        } catch (Exception e) {
            log.warn("Geo normalization failed for '{}': {}", location, e.getMessage());
            return new GeoPoint(null, null);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText(null);
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s.trim())) ? null : s.trim();
    }

    /** Remove reasoning blocks emitted by thinking models (no-op for Gemma; kept for consistency). */
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
