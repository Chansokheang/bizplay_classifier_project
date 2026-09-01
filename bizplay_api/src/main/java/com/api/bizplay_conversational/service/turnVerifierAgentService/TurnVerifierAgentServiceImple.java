package com.api.bizplay_conversational.service.turnVerifierAgentService;

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

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TurnVerifierAgentServiceImple implements TurnVerifierAgentService {

    private static final String SYSTEM_PROMPT = """
            You verify ONE turn of a business-trip form-filling agent. You receive the user's
            message, the draft state BEFORE and AFTER the turn, and the agent's REPLY.
            Answer one question: did the agent's action and reply ALIGN with what the user asked?
            - aligned=no when: the user requested a change or gave a value that AFTER does not
              reflect; the reply ignores the request; or something changed that the user did not
              ask for.
            - aligned=yes when: the action matches the request; the agent asked a reasonable
              clarifying question about something genuinely ambiguous or missing; or it correctly
              explained why a request is not allowed.
            A reply that only asks the NEXT planned question after correctly applying the
            message is aligned.
            NOT misalignment (first shadow run flagged these wrongly):
            - Filling fields with values the user's message STATED (dates, places, names) is the
              agent's job — no confirmation is required for it.
            - A title/content COMPOSED from stated facts (e.g. "오사카 출장") is derived data,
              not an unrequested change.
            - Skipping an optional field after the user declined ("없어") is aligned.
            Flag only real conflicts: a request the draft ignores, a value that contradicts the
            user's words, or a change to something the user explicitly set differently.
            Date-shift direction is fixed vocabulary — do not re-derive it: 늦춰/미뤄/postpone/
            delay/push back = LATER (+N days); 당겨/앞당겨/advance = EARLIER (-N). If AFTER
            reflects that arithmetic, it is aligned.
            Return ONLY JSON, no prose:
            {"aligned": "yes"|"no", "issue": "<one short sentence when no, else empty>"}
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;
    private final com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService;

    @Value("${app.conversational.turn-verifier-agent.model:qwen3-14b}")
    private String modelName;

    public TurnVerifierAgentServiceImple(Map<String, ChatClient> chatClientRegistry,
            LlmSettingsService llmSettingsService, ObjectMapper objectMapper,
            com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
        this.agentPromptService = agentPromptService;
        agentPromptService.registerDefault("turn-verifier", SYSTEM_PROMPT);
    }

    @Override
    public void verifyShadow(String userMessage, String before, String after, String reply,
                             boolean korean) {
        try {
            ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
            if (client == null || userMessage == null || userMessage.isBlank()) {
                return;
            }
            List<Message> prompt = List.of(
                    new SystemMessage(agentPromptService.resolve("turn-verifier", SYSTEM_PROMPT)),
                    new UserMessage("USER MESSAGE:\n" + userMessage
                            + "\n\nDRAFT BEFORE:\n" + before
                            + "\n\nDRAFT AFTER:\n" + after
                            + "\n\nAGENT REPLY:\n" + reply));
            String raw = client.prompt().messages(prompt).call().content();
            String cleaned = raw == null ? "" : raw.replaceAll("(?is)<think>.*?</think>", "")
                    .replaceAll("(?is)</?think>", "").trim();
            int a = cleaned.indexOf('{');
            int b = cleaned.lastIndexOf('}');
            JsonNode parsed = objectMapper.readTree(
                    a >= 0 && b > a ? cleaned.substring(a, b + 1) : "{}");
            boolean aligned = !"no".equalsIgnoreCase(parsed.path("aligned").asText("yes"));
            String issue = parsed.path("issue").asText("");
            if (aligned) {
                log.info("[VERIFY] aligned=yes turn='{}'", trim(userMessage, 80));
            } else {
                // WARN so a day of testing can be measured with one grep.
                log.warn("[VERIFY] aligned=NO issue='{}' turn='{}' reply='{}'",
                        trim(issue, 160), trim(userMessage, 80), trim(reply, 120));
            }
        } catch (Exception e) {
            // Shadow mode must never surface: swallow and note.
            log.info("[VERIFY] verifier unavailable: {}", e.getMessage());
        }
    }

    private static String trim(String s, int n) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > n ? one.substring(0, n) + "…" : one;
    }
}
