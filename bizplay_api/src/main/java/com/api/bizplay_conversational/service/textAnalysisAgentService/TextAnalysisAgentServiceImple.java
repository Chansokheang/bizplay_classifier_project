package com.api.bizplay_conversational.service.textAnalysisAgentService;

import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TextAnalysisAgentServiceImple implements TextAnalysisAgentService {

    private final java.util.Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.text-analysis-agent.model:qwen3-14b}")
    private String modelName;

    private static final String SYSTEM_PROMPT = """
            You analyze a business-trip message and extract trip information.
            Return ONLY a JSON object, no prose, no markdown, no code fences.

            Shape:
            {
              "category": "TRANSPORTATION" | "OTHER",
              "tripDestination": string|null,
              "businessPeriod": string|null,
              "businessStartDate": "YYYY-MM-DD"|null,
              "businessEndDate": "YYYY-MM-DD"|null,
              "tripType": "General business trip" | "Educational business trip" | "Overseas business trip" | null,
              "content": string|null,
              "title": string|null,
              "travelers": [
                { "name": string|null, "origin": string|null, "destination": string|null,
                  "returnPoint": string|null, "transportationMethod": string|null }
              ]
            }

            Rules:
            - category is TRANSPORTATION when the message describes how/where people travel
              (origin, destination, route, return point, method like KTX/flight/bus/train/car),
              or general trip information (destination, dates, period). Otherwise OTHER.
            - tripType: classify the trip into EXACTLY ONE of these three values:
                "Overseas business trip"   -> the destination is in another country (international travel),
                "Educational business trip"-> the trip is for training, education, a seminar, a workshop, or a course,
                "General business trip"    -> anything else (the default for ordinary domestic business).
              Choose the single best fit from the message context. Use null only if there is no trip context at all.
            - content: a short free-text description of what the trip is about (e.g. the meetings or activities).
              This is the description, NOT the type.
            - Use null for anything the message does not state. Do NOT invent destinations, dates, or names.
            - travelers may be empty if no person-specific route is mentioned.
            - Only output the JSON object.
            /no_think
            """;

    /** The three valid business-trip types (출장목적 dropdown). */
    static final String TYPE_GENERAL = "General business trip";
    static final String TYPE_EDUCATIONAL = "Educational business trip";
    static final String TYPE_OVERSEAS = "Overseas business trip";

    @Override
    public TextAnalysisResult analyze(String message, List<Message> history) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            log.warn("Text analysis agent model is not configured: {}", modelName);
            return emptyResult();
        }

        try {
            List<Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(SYSTEM_PROMPT));
            if (history != null) {
                prompt.addAll(history);
            }
            prompt.add(new UserMessage("Analyze this message and extract trip information:\n" + message));

            String content = client.prompt().messages(prompt).call().content();
            log.info("Text analysis agent raw output={}", content);

            String json = extractJson(stripThink(content));
            if (json == null) {
                log.warn("Text analysis agent produced no JSON; returning OTHER.");
                return emptyResult();
            }
            TextAnalysisResult result = objectMapper.readValue(json, TextAnalysisResult.class);
            if (result.getCategory() == null) {
                result.setCategory("OTHER");
            }
            // Normalize the trip type to one of the three canonical values. A recognized trip
            // message (category=TRANSPORTATION) always gets a type, defaulting to General.
            result.setTripType(resolveTripType(result.getTripType(), result.getCategory()));
            return result;
        } catch (Exception e) {
            log.warn("Text analysis failed, returning OTHER: {}", e.getMessage());
            return emptyResult();
        }
    }

    /**
     * Map the model's raw type string to one of the three canonical values. Robust to phrasing
     * ("overseas"/"international", "training"/"seminar", etc.). Returns null when the message does
     * not actually indicate a type — the builder defaults Purpose to General once, only when it is
     * still empty, so a follow-up edit never overwrites a previously chosen type.
     */
    private String resolveTripType(String raw, String category) {
        if (raw == null) {
            return null;
        }
        String s = raw.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("overseas") || s.contains("oversea") || s.contains("international")
                || s.contains("abroad") || s.contains("해외")) {
            return TYPE_OVERSEAS;
        }
        if (s.contains("educat") || s.contains("training") || s.contains("seminar")
                || s.contains("workshop") || s.contains("course") || s.contains("교육")) {
            return TYPE_EDUCATIONAL;
        }
        if (s.contains("general") || s.contains("일반")) {
            return TYPE_GENERAL;
        }
        return null;
    }

    private TextAnalysisResult emptyResult() {
        TextAnalysisResult result = new TextAnalysisResult();
        result.setCategory("OTHER");
        return result;
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
