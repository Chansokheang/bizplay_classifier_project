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
            asking ONLY for the field(s) listed — usually a single one. Ask for that field and
            nothing else: never mention other fields, never bundle several asks into one turn,
            never list what else remains. Mention the field by its given (often Korean) label.
            Sound like a helpful colleague chatting, not a system or a form: conversational phrasing,
            no fixed templates, no greetings, no field=value dumps, no internal jargon.
            Plain text only — no JSON, no markdown, no preamble.
            CRITICAL: the question sentence must be written entirely in %1$s, even though the
            field labels may be in another language. Do NOT answer in the labels' language.
            Reply in %1$s only.
            /no_think
            """;

    private static final String QA_PROMPT = """
            You answer a user's question about their in-progress business-trip form using ONLY
            the draft data provided (JSON — keys and values may be Korean) and the RECENT
            CONVERSATION section when present. Lines marked [UI] there record actions the
            user performed in the app (approval-line picks, saves) — they are facts and MAY
            be used to answer questions about the approval line or what happened so far.
            - Asked for everything ("show all the details", "전체 보여줘"): give a clean summary,
              one short line per FILLED field, using the field labels. Skip empty/null values.
            - Asked about specific fields: answer just those.
            - A value not present in the data is simply not set yet — say so. NEVER invent values,
              and copy stored values verbatim.
            - Show only what a PERSON filled in: form/purpose, title, content, dates, places,
              travelers, and custom item values. Skip internal keys (ids, codes, erpCode,
              paperId, userReqId, flags) entirely.
            Plain text only — no JSON, no markdown, no preamble.
            CRITICAL: write the reply in %1$s (stored values stay verbatim as they are).
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
        agentPromptService.registerDefault("draft-qa", QA_PROMPT);
    }

    @Override
    public String answerDraftQuestion(String draftContextJson, String question, boolean korean) {
        ChatClient primary = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        ChatClient alt = chatClientRegistry.get(modelName);
        ChatClient[] attempts = (alt != null && alt != primary)
                ? new ChatClient[]{primary, primary, alt}
                : new ChatClient[]{primary, primary};
        List<Message> prompt = List.of(
                new SystemMessage(agentPromptService.resolve("draft-qa", QA_PROMPT)
                        .formatted(korean ? "Korean" : "English")),
                new UserMessage("DRAFT DATA:\n" + draftContextJson + "\n\nUSER QUESTION:\n" + question));
        for (ChatClient client : attempts) {
            if (client == null) {
                continue;
            }
            try {
                String reply = client.prompt().messages(prompt).call().content();
                String cleaned = reply == null ? "" : reply.replaceAll("(?is)<think>.*?</think>", "").trim();
                // No language check here — stored values legitimately stay in their own language.
                if (!cleaned.isBlank()) {
                    return cleaned;
                }
            } catch (Exception e) {
                log.warn("Draft Q&A failed ({}); retrying.", e.getMessage());
            }
        }
        return korean
                ? "지금은 요약을 만들지 못했어요 — 미리보기 카드를 확인해 주세요."
                : "I couldn't put the summary together just now — please check the preview cards.";
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
