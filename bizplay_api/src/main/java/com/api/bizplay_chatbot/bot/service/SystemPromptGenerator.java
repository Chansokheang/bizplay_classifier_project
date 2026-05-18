package com.api.bizplay_chatbot.bot.service;

import com.api.bizplay_chatbot.bot.dto.SystemPromptGenerationRequest;
import com.api.bizplay_chatbot.bot.dto.SystemPromptGenerationResponse;
import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.config.BotDefaults;
import com.api.bizplay_chatbot.config.LlmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Drafts a system prompt for a new bot based on its name and description.
 * Used by the bot-creation UI as a "Generate" button so users don't need to
 * write a prompt from scratch.
 *
 * The generator calls an LLM with a meta-prompt that constrains the output
 * to the same three-step source priority the rest of the pipeline relies on
 * (context → history → refuse). If the LLM returns nothing usable, callers
 * fall back to {@link BotDefaults#DEFAULT_SYSTEM_PROMPT}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemPromptGenerator {

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmProperties llmProperties;

    /** Stricter than the chat-time temperature so different "Generate" clicks
     *  produce the same prompt for the same input — predictable UX. */
    private static final double GENERATION_TEMPERATURE = 0.2;
    private static final int    GENERATION_MAX_TOKENS  = 600;

    // Meta-prompts come in language-matched pairs because EXAONE-7.8B follows
    // a Korean-instruction prompt back in Korean and an English one back in
    // English far more reliably than it follows an abstract "match the user's
    // language" rule buried in a numbered list. We pick the variant by scanning
    // the bot name + description for Hangul characters.

    private static final String META_SYSTEM_PROMPT_EN = """
            You are an expert at writing system prompts for retrieval-augmented chatbots.
            Output ONLY the resulting system prompt for the bot — no preamble, no labels, \
            no quotes wrapping it, no markdown.
            """;

    private static final String META_USER_TEMPLATE_EN = """
            Write ONE concise system prompt in ENGLISH (3–5 sentences) for the bot described below. \
            The generated prompt MUST:
            1. Identify the bot as an assistant for the specific topic implied by the description.
            2. Tell the model to use the provided context documents as the primary source.
            3. Tell the model to fall back to the conversation history if the documents don't \
               cover the question.
            4. Tell the model that if NEITHER source has the information, reply with exactly: \
               "I don't have enough information to answer this question." (keep this phrase in \
               English regardless of the bot's response language — the source-suppression check \
               relies on it).
            5. Forbid using outside knowledge or making assumptions.

            Bot name: {name}
            Description: {description}
            """;

    private static final String META_SYSTEM_PROMPT_KR = """
            당신은 검색-증강(Retrieval-Augmented) 챗봇용 시스템 프롬프트 작성 전문가입니다.
            출력은 오직 봇의 시스템 프롬프트 한 가지만 작성하세요. 서문, 라벨, 따옴표 \
            감싸기, 마크다운 없이.
            """;

    private static final String META_USER_TEMPLATE_KR = """
            아래 설명된 봇을 위해 한국어로 된 간결한 시스템 프롬프트(3~5문장)를 작성하세요. \
            생성된 프롬프트는 반드시 다음 조건을 충족해야 합니다:
            1. 설명에 함의된 특정 주제의 어시스턴트로 봇을 정의할 것.
            2. 제공된 컨텍스트 문서를 1차 정보원으로 사용하도록 모델에게 지시할 것.
            3. 문서가 질문을 다루지 않을 경우 대화 기록을 사용하도록 지시할 것.
            4. 두 정보원 모두 정보를 갖고 있지 않을 경우, 정확히 다음과 같이 응답하도록 \
               지시할 것: "I don't have enough information to answer this question." \
               (이 문구는 영어로 유지하세요 — 소스 표시 차단 로직이 이 문구에 의존합니다.)
            5. 외부 지식 사용이나 추측을 금지할 것.
            6. 전체 프롬프트는 한국어로 작성할 것.

            봇 이름: {name}
            설명: {description}
            """;

    public SystemPromptGenerationResponse generate(SystemPromptGenerationRequest req) {
        String modelName = (req.getLlmModel() == null || req.getLlmModel().isBlank())
                ? llmProperties.getDefaultModel()
                : req.getLlmModel();

        ChatClient client = chatClientRegistry.get(modelName);
        if (client == null) {
            throw new BusinessException(
                    "Unknown LLM model '" + modelName + "'. Available: " + chatClientRegistry.keySet(),
                    HttpStatus.BAD_REQUEST);
        }

        // Pick language-matched meta-prompts based on the bot's name +
        // description. EXAONE follows Korean instructions back in Korean and
        // English instructions back in English far more reliably than an
        // abstract "match the input language" rule would.
        boolean korean = containsKorean(req.getName())
                || (req.getDescription() != null && containsKorean(req.getDescription()));
        String metaSystem  = korean ? META_SYSTEM_PROMPT_KR : META_SYSTEM_PROMPT_EN;
        String metaUserTpl = korean ? META_USER_TEMPLATE_KR : META_USER_TEMPLATE_EN;

        String description = req.getDescription() == null || req.getDescription().isBlank()
                ? (korean
                        ? "(설명이 제공되지 않았습니다 — 봇 이름에서 목적을 추론하세요)"
                        : "(no description supplied — infer the bot's purpose from its name)")
                : req.getDescription();

        String generated;
        try {
            generated = client.prompt()
                    .system(metaSystem)
                    .options(ChatOptions.builder()
                            .temperature(GENERATION_TEMPERATURE)
                            .maxTokens(GENERATION_MAX_TOKENS)
                            .build())
                    .user(u -> u.text(metaUserTpl)
                            .param("name", req.getName())
                            .param("description", description))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("System prompt generation failed, returning the static default. cause={}", e.getMessage());
            return SystemPromptGenerationResponse.builder()
                    .systemPrompt(BotDefaults.DEFAULT_SYSTEM_PROMPT)
                    .llmModel(modelName)
                    .build();
        }

        // Defensive trim/strip: small models sometimes wrap output in code
        // fences or quotes despite the instruction not to.
        String cleaned = clean(generated);
        if (cleaned.isBlank()) {
            log.warn("System prompt generation returned blank, falling back to default.");
            cleaned = BotDefaults.DEFAULT_SYSTEM_PROMPT;
        }
        log.info("Generated system prompt: model={}, name=\"{}\", chars={}",
                modelName, req.getName(), cleaned.length());

        return SystemPromptGenerationResponse.builder()
                .systemPrompt(cleaned)
                .llmModel(modelName)
                .build();
    }

    /** Hangul detection: matches the same code-point ranges ChatService uses
     *  (Syllables, Jamo, Compatibility Jamo). One Korean character anywhere
     *  in the input is enough to pick the Korean meta-prompt — bot owners
     *  often mix English loanwords into Korean descriptions. */
    private static boolean containsKorean(String text) {
        if (text == null) return false;
        return text.codePoints().anyMatch(cp ->
                (cp >= 0xAC00 && cp <= 0xD7AF)
             || (cp >= 0x1100 && cp <= 0x11FF)
             || (cp >= 0x3130 && cp <= 0x318F));
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        // Strip code fences (```...```)
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.strip();
        }
        // Strip surrounding ASCII or curly quotes if present
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"')
                    || (first == '\'' && last == '\'')
                    || (first == '“' && last == '”')
                    || (first == '‘' && last == '’')) {
                s = s.substring(1, s.length() - 1).strip();
            }
        }
        return s;
    }
}
