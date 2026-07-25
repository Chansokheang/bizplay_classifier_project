package com.api.bizplay_conversational.service.formFollowUpAgentService;

import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FormFollowUpAgentServiceImple implements FormFollowUpAgentService {

    private static final String SYSTEM_PROMPT = """
            You help a user complete a business-trip form. Write ONE short, friendly English question
            asking for the missing fields, mentioning each field by its given (often Korean) label.
            Plain text only — no JSON, no markdown, no preamble.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;

    @Value("${app.conversational.form-follow-up-agent.model:qwen3-14b}")
    private String modelName;

    public FormFollowUpAgentServiceImple(Map<String, ChatClient> chatClientRegistry,
                                         LlmSettingsService llmSettingsService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
    }

    @Override
    public String composeFollowUp(String paperName, List<String> missingLabels) {
        if (missingLabels == null || missingLabels.isEmpty()) {
            return null;
        }
        String fallback = "To complete the "
                + (paperName == null || paperName.isBlank() ? "trip plan form" : "'" + paperName + "'")
                + ", please provide: " + String.join(", ", missingLabels) + ".";
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            return fallback;
        }
        try {
            List<Message> prompt = List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage("Form: " + paperName + "\nMissing fields: " + String.join(", ", missingLabels)));
            String reply = client.prompt().messages(prompt).call().content();
            String cleaned = reply == null ? "" : reply.replaceAll("(?is)<think>.*?</think>", "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        } catch (Exception e) {
            log.warn("Follow-up composition failed ({}); using fallback.", e.getMessage());
            return fallback;
        }
    }
}
