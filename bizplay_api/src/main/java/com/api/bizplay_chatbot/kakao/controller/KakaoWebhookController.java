package com.api.bizplay_chatbot.kakao.controller;

import com.api.bizplay_chatbot.domain.entity.Bot;
import com.api.bizplay_chatbot.kakao.dto.SkillRequest;
import com.api.bizplay_chatbot.kakao.dto.SkillResponse;
import com.api.bizplay_chatbot.kakao.service.KakaoBotService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint Kakao i Openbuilder POSTs every Skill webhook call to.
 *
 * <p>Auth is by per-bot opaque secret in the URL path. The webhook URL
 * (origin + path + secret) is sensitive — operators paste it into
 * Openbuilder's Skill config; anyone holding it can replay arbitrary
 * messages to the bot. Treat as a credential.
 *
 * <p>Path also carries the {@code botId} but it's only used as a
 * tie-breaker: the real lookup is by secret (constant-time-uniquely
 * identifies the bot via the partial-unique index). If the secret resolves
 * to a bot whose id doesn't match the path, we still return 404 to avoid
 * leaking which botIds exist.
 *
 * <p>Hidden from Swagger — the request body shape is Kakao's, not ours, and
 * the URL contains a secret operators shouldn't see in public docs.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/kakao/webhook")
@RequiredArgsConstructor
public class KakaoWebhookController {

    private final KakaoBotService kakaoBotService;

    @PostMapping("/{botId}/{secret}")
    public ResponseEntity<SkillResponse> receive(
            @PathVariable UUID botId,
            @PathVariable String secret,
            @RequestBody SkillRequest request) {

        Bot bot = kakaoBotService.resolveByWebhookSecret(secret);
        // Two failure modes collapsed into one 404: bad secret OR mismatched
        // botId-in-path. Either way the response is indistinguishable to a
        // would-be attacker, so they can't probe valid botIds by varying
        // the path alone.
        if (bot == null || !bot.getId().equals(botId)) {
            log.warn("Kakao webhook rejected: botId={}, secret-match={}",
                    botId, bot != null);
            return ResponseEntity.notFound().build();
        }

        // Compose and return the synchronous response. For normal traffic
        // this is "useCallback: true" — the async path then delivers the
        // real answer. Disabled bots or misconfigured Skills get a direct
        // simpleText reply and never reach the async path.
        SkillResponse syncResponse = kakaoBotService.buildSyncAck(request, bot);

        if (Boolean.TRUE.equals(syncResponse.getUseCallback())) {
            // Fire-and-forget; @Async returns immediately and the worker
            // pool handles the LLM call + callback POST.
            kakaoBotService.processAsyncReply(bot.getId(), request);
        }

        return ResponseEntity.ok(syncResponse);
    }
}
