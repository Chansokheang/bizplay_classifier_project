package com.api.bizplay_chatbot.kakao.service;

import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.config.KakaoProperties;
import com.api.bizplay_chatbot.domain.entity.Bot;
import com.api.bizplay_chatbot.domain.entity.KakaoChat;
import com.api.bizplay_chatbot.domain.repository.BotRepository;
import com.api.bizplay_chatbot.domain.repository.KakaoChatRepository;
import com.api.bizplay_chatbot.kakao.client.KakaoApiClient;
import com.api.bizplay_chatbot.kakao.dto.KakaoStatusResponse;
import com.api.bizplay_chatbot.kakao.dto.OutputComponent;
import com.api.bizplay_chatbot.kakao.dto.SimpleText;
import com.api.bizplay_chatbot.kakao.dto.SkillRequest;
import com.api.bizplay_chatbot.kakao.dto.SkillResponse;
import com.api.bizplay_chatbot.kakao.dto.SkillTemplate;
import com.api.bizplay_chatbot.kakao.dto.SkillUserRequest;
import com.api.bizplay_chatbot.rag.chat.dto.ChatRequest;
import com.api.bizplay_chatbot.rag.chat.dto.ChatResponse;
import com.api.bizplay_chatbot.rag.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KakaoTalk channel adapter — manages per-bot Kakao integration lifecycle
 * (configure / unconfigure) and processes inbound Skill webhook calls.
 *
 * <p><b>Inbound flow (callback pattern):</b>
 * <ol>
 *   <li>Kakao POSTs the user message to {@code /chatbot/api/v1/kakao/webhook/{botId}/{secret}}.</li>
 *   <li>The controller calls {@link #buildSyncAck(SkillRequest, Bot)}, returning
 *       a "useCallback: true" response inside the 5s budget.</li>
 *   <li>The controller also fires {@link #processAsyncReply(UUID, SkillRequest)}
 *       which runs the LLM call on {@code kakaoTaskExecutor} and POSTs the
 *       final answer to {@code userRequest.callbackUrl}.</li>
 * </ol>
 *
 * <p>Synchronous fast-paths (disabled bots, missing callback URL, etc.) skip
 * the callback hand-off and answer directly in the sync response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoBotService {

    /** Kakao's per-simpleText character limit ({@code 1000자}). Note: characters,
     *  not bytes — the earlier byte-based cap truncated Korean replies at
     *  roughly 333 characters because each Hangul code point is 3 UTF-8 bytes. */
    private static final int MAX_TEXT_CHARS = 1000;
    /** Kakao's {@code template.outputs} length limit. Three simpleText bubbles
     *  give us up to 3000 chars of headroom — enough for any LLM answer that
     *  fits in the bot's {@code maxAnswerLength} setting. */
    private static final int MAX_OUTPUTS = 3;

    private final BotRepository botRepository;
    private final KakaoChatRepository kakaoChatRepository;
    private final ChatService chatService;
    private final KakaoApiClient kakaoApiClient;
    private final KakaoProperties kakaoProperties;

    // ─── Configure / status ──────────────────────────────────────────────

    @Transactional
    public KakaoStatusResponse configure(UUID botId, String botName) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BusinessException("Bot not found: " + botId, HttpStatus.NOT_FOUND));

        // Generate a fresh secret on every configure — operators copy a new
        // URL into Openbuilder, and a stale leaked URL becomes invalid.
        String secret = UUID.randomUUID().toString().replace("-", "");
        bot.setKakaoWebhookSecret(secret);
        bot.setKakaoBotName(botName);
        bot.setKakaoConfiguredAt(LocalDateTime.now());
        botRepository.save(bot);

        log.info("Kakao configured: botId={}, botName={}", botId, botName);
        return toStatus(bot);
    }

    @Transactional(readOnly = true)
    public KakaoStatusResponse status(UUID botId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BusinessException("Bot not found: " + botId, HttpStatus.NOT_FOUND));
        return toStatus(bot);
    }

    @Transactional
    public KakaoStatusResponse unconfigure(UUID botId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BusinessException("Bot not found: " + botId, HttpStatus.NOT_FOUND));
        bot.setKakaoWebhookSecret(null);
        bot.setKakaoBotName(null);
        bot.setKakaoConfiguredAt(null);
        botRepository.save(bot);
        log.info("Kakao unconfigured: botId={}", botId);
        return KakaoStatusResponse.builder().kakaoConfigured(false).build();
    }

    /** Resolve a bot by the secret on the inbound webhook path. Returns null
     *  on miss — the webhook controller turns that into a 404, indistinguishable
     *  from "no such bot" so an attacker can't probe which botIds exist. */
    @Transactional(readOnly = true)
    public Bot resolveByWebhookSecret(String secret) {
        if (secret == null || secret.isBlank()) return null;
        return botRepository.findByKakaoWebhookSecret(secret).orElse(null);
    }

    // ─── Inbound webhook ─────────────────────────────────────────────────

    /**
     * Build the synchronous response Kakao receives within its 5s window.
     * Three branches:
     *
     * <ul>
     *   <li><b>Disabled bot</b> → reply directly with a "currently unavailable"
     *       simpleText. No callback needed; the answer is short and the bot
     *       did no work.</li>
     *   <li><b>No callbackUrl</b> → either the Openbuilder Skill tester
     *       (which intentionally omits {@code callbackUrl} in its synthetic
     *       test payloads — verified by inspecting the tester JSON) or a
     *       real chat session where the operator forgot to enable
     *       {@code 콜백 사용} (Use Callback). The reply explains both
     *       possibilities so neither audience is left confused.</li>
     *   <li><b>Normal</b> → "useCallback: true" + a placeholder bubble. The
     *       async worker handles the rest.</li>
     * </ul>
     */
    public SkillResponse buildSyncAck(SkillRequest request, Bot bot) {
        if (bot.isDisabled()) {
            return simpleTextResponse("This bot is currently unavailable. Please try again later.");
        }
        SkillUserRequest userReq = request.getUserRequest();
        if (userReq == null || userReq.getCallbackUrl() == null || userReq.getCallbackUrl().isBlank()) {
            log.info("Kakao Skill request has no callbackUrl — likely Openbuilder's Skill tester " +
                    "(which omits it by design). botId={}", bot.getId());
            return simpleTextResponse(
                    "✓ Webhook reachable. This request has no callbackUrl — normal for Kakao i " +
                    "Openbuilder's Skill tester. To verify the full RAG flow, send a real message " +
                    "through KakaoTalk. (If you see this reply during a real chat, enable " +
                    "'콜백 사용' / Use Callback in your Skill configuration and redeploy.)");
        }
        return SkillResponse.builder()
                .useCallback(true)
                .data(SkillResponse.CallbackData.builder()
                        .text(kakaoProperties.getPlaceholderText())
                        .build())
                .build();
    }

    /**
     * Run the LLM call asynchronously and POST the final answer to the
     * callback URL Kakao provided. Always called AFTER the controller has
     * sent its synchronous response, so failures here only affect the user's
     * eventual reply, not the 5s budget.
     *
     * <p>Top-level try/catch is required — async exceptions are otherwise
     * silently swallowed by Spring's default handler, and the user would
     * stare at the placeholder bubble forever.
     */
    @Async("kakaoTaskExecutor")
    public void processAsyncReply(UUID botId, SkillRequest request) {
        try {
            doProcessAsyncReply(botId, request);
        } catch (RuntimeException ex) {
            log.error("Kakao async reply failed for botId={}: {}", botId, ex.getMessage(), ex);
            // Best-effort apology to the user; if THIS fails, give up.
            try {
                String callbackUrl = request.getUserRequest() != null
                        ? request.getUserRequest().getCallbackUrl()
                        : null;
                if (callbackUrl != null) {
                    kakaoApiClient.postCallback(callbackUrl,
                            simpleTextResponse("Sorry, I hit an internal error. Please try again in a moment."));
                }
            } catch (RuntimeException nested) {
                log.warn("Failed to deliver Kakao fallback error message: {}", nested.getMessage());
            }
        }
    }

    private void doProcessAsyncReply(UUID botId, SkillRequest request) {
        SkillUserRequest userReq = request.getUserRequest();
        String callbackUrl = userReq.getCallbackUrl();
        String utterance = userReq.getUtterance();
        String kakaoUserId = (userReq.getUser() != null) ? userReq.getUser().getId() : null;

        if (utterance == null || utterance.isBlank() || kakaoUserId == null) {
            log.debug("Kakao update has no actionable utterance/user, dropping. botId={}", botId);
            return;
        }

        // Bot reload mid-callback isn't strictly needed (we already have the
        // entity from the sync path), but the async worker runs after the
        // sync transaction has closed — fetching fresh keeps us decoupled.
        Bot bot = botRepository.findById(botId).orElse(null);
        if (bot == null || bot.getKakaoWebhookSecret() == null) {
            log.warn("Kakao async reply: bot vanished or unconfigured mid-flight. botId={}", botId);
            return;
        }
        if (bot.isDisabled()) {
            // Bot was disabled between the sync ack and the async run — send
            // the same polite message via callback so the user isn't left
            // hanging on the placeholder.
            kakaoApiClient.postCallback(callbackUrl,
                    simpleTextResponse("This bot is currently unavailable. Please try again later."));
            return;
        }

        Optional<KakaoChat> mapping = kakaoChatRepository.findByBotIdAndKakaoUserId(botId, kakaoUserId);
        UUID existingSessionId = mapping.map(KakaoChat::getSessionId).orElse(null);

        ChatRequest chatRequest = new ChatRequest(botId, utterance, existingSessionId, "kakaotalk");
        ChatResponse chatResponse = chatService.chat(chatRequest);

        upsertMapping(botId, kakaoUserId, chatResponse.getSessionId());

        String answer = chatResponse.getAnswer();
        if (answer == null || answer.isBlank()) {
            answer = "I couldn't generate a response. Please try again.";
        }

        // Split across up to MAX_OUTPUTS simpleText bubbles so long answers
        // (typical for a verbose RAG response) aren't truncated. Splitting
        // happens at paragraph / line / space boundaries when possible so
        // the bubble breaks read naturally.
        kakaoApiClient.postCallback(callbackUrl,
                outputsResponse(splitForKakao(answer)));
    }

    private void upsertMapping(UUID botId, String kakaoUserId, UUID sessionId) {
        Optional<KakaoChat> existing = kakaoChatRepository.findByBotIdAndKakaoUserId(botId, kakaoUserId);
        KakaoChat row = existing.orElseGet(() -> {
            KakaoChat fresh = new KakaoChat();
            fresh.setId(UUID.randomUUID());
            fresh.setBotId(botId);
            fresh.setKakaoUserId(kakaoUserId);
            fresh.setCreatedAt(LocalDateTime.now());
            return fresh;
        });
        row.setSessionId(sessionId);
        row.setLastMessageAt(LocalDateTime.now());
        kakaoChatRepository.save(row);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private static SkillResponse simpleTextResponse(String text) {
        return SkillResponse.builder()
                .template(new SkillTemplate(List.of(new OutputComponent(new SimpleText(text)))))
                .build();
    }

    private static SkillResponse outputsResponse(List<OutputComponent> outputs) {
        return SkillResponse.builder()
                .template(new SkillTemplate(outputs))
                .build();
    }

    /**
     * Split a long answer across up to {@link #MAX_OUTPUTS} simpleText bubbles,
     * each at most {@link #MAX_TEXT_CHARS} chars. Splits prefer paragraph,
     * then line, then space boundaries within the last 30% of each bubble's
     * budget so the bubble breaks read naturally; falls back to a hard cut
     * only when no nice boundary exists. If the answer still overflows the
     * final bubble after MAX_OUTPUTS-1 splits, the last bubble is truncated
     * with an ellipsis so we never return more than what Kakao accepts.
     */
    static List<OutputComponent> splitForKakao(String answer) {
        List<OutputComponent> outputs = new ArrayList<>();
        String remaining = answer;
        while (remaining.length() > MAX_TEXT_CHARS && outputs.size() < MAX_OUTPUTS - 1) {
            int cut = preferredSplitPoint(remaining, MAX_TEXT_CHARS);
            outputs.add(new OutputComponent(new SimpleText(remaining.substring(0, cut).stripTrailing())));
            remaining = remaining.substring(cut).stripLeading();
        }
        if (remaining.length() > MAX_TEXT_CHARS) {
            remaining = remaining.substring(0, MAX_TEXT_CHARS - 1) + "…";
        }
        outputs.add(new OutputComponent(new SimpleText(remaining)));
        return outputs;
    }

    /** Look for the latest paragraph / line / space boundary within the last
     *  30% of {@code hardMax}. Returning {@code hardMax} means a hard cut —
     *  acceptable but visually less clean than a boundary break. */
    private static int preferredSplitPoint(String text, int hardMax) {
        int min = hardMax * 7 / 10;
        int paragraph = text.lastIndexOf("\n\n", hardMax);
        if (paragraph >= min) return paragraph + 2;
        int line = text.lastIndexOf('\n', hardMax);
        if (line >= min) return line + 1;
        int space = text.lastIndexOf(' ', hardMax);
        if (space >= min) return space + 1;
        return hardMax;
    }

    private KakaoStatusResponse toStatus(Bot bot) {
        boolean configured = bot.getKakaoWebhookSecret() != null;
        String publicBase = kakaoProperties.getPublicBaseUrl();
        boolean baseMissing = publicBase == null || publicBase.isBlank();
        String url = null;
        if (configured && !baseMissing) {
            String trimmed = publicBase.endsWith("/")
                    ? publicBase.substring(0, publicBase.length() - 1)
                    : publicBase;
            url = trimmed + "/chatbot/api/v1/kakao/webhook/" + bot.getId() + "/" + bot.getKakaoWebhookSecret();
        }
        return KakaoStatusResponse.builder()
                .kakaoConfigured(configured)
                .botName(bot.getKakaoBotName())
                .webhookUrl(url)
                .publicBaseUrlMissing(configured && baseMissing)
                .configuredAt(bot.getKakaoConfiguredAt())
                .build();
    }
}
