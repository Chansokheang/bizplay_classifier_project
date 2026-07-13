package com.api.bizplay_conversational.service.staffLookupAgentService;

import com.api.bizplay_conversational.model.request.StaffLookupAgentRequest;
import com.api.bizplay_conversational.model.response.StaffLookupAgentResponse;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.service.staffService.StaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffLookupAgentServiceImple implements StaffLookupAgentService {

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final StaffService staffService;

    @Value("${app.conversational.staff-lookup-agent.model:qwen3-14b}")
    private String modelName;

    @Override
    public StaffLookupAgentResponse lookup(StaffLookupAgentRequest request) {
        String extractedName = normalize(request.getName());
        boolean llmUsed = false;
        long llmElapsedMs = 0;
        String llmError = null;

        if (extractedName == null) {
            long startedAt = System.nanoTime();
            llmUsed = true;
            try {
                extractedName = extractStaffName(request.getMessage());
            } catch (RuntimeException e) {
                llmError = e.getMessage();
                log.warn("LLM staff extraction failed: {}", e.getMessage(), e);
            } finally {
                llmElapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            }
        } else {
            log.info("Skipping LLM staff extraction because request.name was provided: {}", extractedName);
        }

        StaffLookupResult result = staffService.lookup(request.getCorpNo(), extractedName);
        return StaffLookupAgentResponse.builder()
                .extractedName(extractedName)
                .llmModel(modelName)
                .llmUsed(llmUsed)
                .llmElapsedMs(llmElapsedMs)
                .llmError(llmError)
                .result(result)
                .build();
    }

    private String extractStaffName(String message) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            throw new IllegalStateException("Staff lookup agent model is not configured: " + modelName);
        }

        log.info("Calling LLM model={} for staff name extraction", modelName);
        String content = client.prompt()
                .system("""
                        Extract one employee/staff name from the user's message.
                        Return only the name text.
                        If no staff name is present, return NONE.
                        Do not add explanations, JSON, markdown, or punctuation.
                        /no_think
                        """)
                .user(message)
                .call()
                .content();
        log.info("LLM model={} staff extraction raw output={}", modelName, content);

        String normalized = normalize(stripThink(content));
        if (normalized == null || "NONE".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized.replace("\"", "").trim();
    }

    /** Remove reasoning blocks emitted by thinking models (e.g. Qwen3 &lt;think&gt;...&lt;/think&gt;). */
    private String stripThink(String value) {
        if (value == null) {
            return null;
        }
        // Drop a complete <think>...</think> block, then any dangling open/close tag remnants.
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)</?think>", "");
        return cleaned.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
