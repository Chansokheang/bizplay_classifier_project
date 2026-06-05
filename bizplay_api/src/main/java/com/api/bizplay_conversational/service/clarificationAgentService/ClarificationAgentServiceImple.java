package com.api.bizplay_conversational.service.clarificationAgentService;

import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.MissingFieldsResult;
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
 * LLM-backed Clarification / Field-Completion agent. Given the deterministic missing-field report,
 * it phrases ONE concise follow-up asking the user for what is still needed. If the draft is already
 * complete it produces a "ready to create" confirmation instead. All LLM failures degrade gracefully
 * to a deterministic template so the turn never breaks.
 */
@Slf4j
@Service
public class ClarificationAgentServiceImple implements ClarificationAgentService {

    private static final String READY_MESSAGE =
            "All the required details are captured. Shall I create the trip plan? Reply \"approve\" to proceed.";

    private final Map<String, ChatClient> chatClientRegistry;

    @Value("${app.conversational.clarification-agent.model:qwen3-14b}")
    private String modelName;

    public ClarificationAgentServiceImple(Map<String, ChatClient> chatClientRegistry) {
        this.chatClientRegistry = chatClientRegistry;
    }

    @Override
    public String composeFollowUp(TripPlanDraft draft, MissingFieldsResult missing, List<Message> history) {
        if (missing == null || missing.isComplete()) {
            return READY_MESSAGE;
        }
        List<String> fields = missing.getMissing();
        if (fields == null || fields.isEmpty()) {
            return READY_MESSAGE;
        }

        ChatClient client = chatClientRegistry.get(modelName);
        if (client == null) {
            log.warn("Clarification agent model '{}' not configured; using template follow-up.", modelName);
            return templateFollowUp(fields);
        }

        try {
            String systemPrompt = """
                    You are helping a user fill in a business-trip plan. Some required details are still missing.
                    Write ONE short, friendly message (1-2 sentences) that asks the user to provide ONLY the missing
                    items listed below. Do not invent fields, do not ask for anything not in the list, and do not
                    restate what is already known. Plain text only: no JSON, no markdown, no bullet list.
                    /no_think
                    """;
            String userPrompt = "Missing fields:\n- " + String.join("\n- ", fields);

            List<Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(systemPrompt));
            if (history != null) {
                prompt.addAll(history);
            }
            prompt.add(new UserMessage(userPrompt));

            String content = client.prompt().messages(prompt).call().content();
            String cleaned = stripThink(content);
            if (cleaned == null || cleaned.isBlank()) {
                return templateFollowUp(fields);
            }
            return cleaned;
        } catch (RuntimeException e) {
            log.warn("Clarification agent LLM call failed; using template follow-up: {}", e.getMessage());
            return templateFollowUp(fields);
        }
    }

    /** Deterministic fallback used when the LLM is unavailable or returns nothing usable. */
    private String templateFollowUp(List<String> fields) {
        return "To finish the trip plan, I still need: " + String.join(", ", fields) + ".";
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
}
