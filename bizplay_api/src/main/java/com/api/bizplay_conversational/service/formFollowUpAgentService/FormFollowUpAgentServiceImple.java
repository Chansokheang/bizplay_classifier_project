package com.api.bizplay_conversational.service.formFollowUpAgentService;

import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;
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
            You help a user complete a business-trip form. Write ONE short, natural question
            asking for the missing fields, mentioning each field by its given (often Korean) label.
            Sound like a helpful colleague chatting, not a system or a form: conversational phrasing,
            no fixed templates, no greetings, no field=value dumps, no internal jargon.
            Plain text only — no JSON, no markdown, no preamble.
            CRITICAL: the question sentence must be written entirely in %1$s, even though the
            field labels may be in another language. Do NOT answer in the labels' language.
            Reply in %1$s only.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final AgentPromptService agentPromptService;

    @Value("${app.conversational.form-follow-up-agent.model:qwen3-14b}")
    private String modelName;

    public FormFollowUpAgentServiceImple(Map<String, ChatClient> chatClientRegistry,
                                         LlmSettingsService llmSettingsService,
                                         AgentPromptService agentPromptService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.agentPromptService = agentPromptService;
        agentPromptService.registerDefault("form-follow-up", SYSTEM_PROMPT);
    }

    @Override
    public String composeFollowUp(String paperName, List<String> missingLabels, boolean korean) {
        if (missingLabels == null || missingLabels.isEmpty()) {
            return null;
        }
        String labels = String.join(", ", missingLabels);
        String fallback = korean
                ? (paperName == null || paperName.isBlank() ? "계획을" : "'" + paperName + "'을(를)")
                        + " 완성하려면 다음 정보가 필요해요: " + labels + "."
                : "To finish the "
                        + (paperName == null || paperName.isBlank() ? "trip plan" : "'" + paperName + "'")
                        + ", could you tell me: " + labels + "?";
        // Blank-safe: reasoning models (e.g. LUXIA as the active override) can return EMPTY
        // content — retry, then try the configured model WITHOUT the override, before ever
        // falling back to the canned template sentence.
        ChatClient primary = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        ChatClient alt = chatClientRegistry.get(modelName);
        ChatClient[] attempts = (alt != null && alt != primary)
                ? new ChatClient[]{primary, primary, alt}
                : new ChatClient[]{primary, primary};
        List<Message> prompt = List.of(
                new SystemMessage(agentPromptService.resolve("form-follow-up", SYSTEM_PROMPT)
                        .formatted(korean ? "Korean" : "English")),
                new UserMessage("Form: " + paperName + "\nMissing fields: " + labels));
        for (ChatClient client : attempts) {
            if (client == null) {
                continue;
            }
            try {
                String reply = client.prompt().messages(prompt).call().content();
                String cleaned = reply == null ? "" : reply.replaceAll("(?is)<think>.*?</think>", "").trim();
                if (!cleaned.isBlank() && !wrongLanguage(cleaned, missingLabels, korean)) {
                    return cleaned;
                }
            } catch (Exception e) {
                log.warn("Follow-up composition failed ({}); retrying.", e.getMessage());
            }
        }
        return fallback;
    }

    /**
     * Small models drift into the field labels' language despite the instruction. Strip the
     * labels out, then check the sentence that remains: Hangul in an English turn (or a run of
     * Latin words in a Korean turn) means the model missed the language — use the fallback.
     */
    private boolean wrongLanguage(String reply, List<String> labels, boolean korean) {
        String sentence = reply;
        for (String label : labels) {
            sentence = sentence.replace(label, "");
        }
        if (!korean) {
            return sentence.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
        }
        return sentence.matches("(?s).*\\b[A-Za-z]+\\s+[A-Za-z]+\\s+[A-Za-z]+\\b.*");
    }
}
