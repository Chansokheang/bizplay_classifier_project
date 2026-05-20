package com.api.bizplay_chatbot.kakao.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_chatbot.kakao.dto.KakaoConfigureRequest;
import com.api.bizplay_chatbot.kakao.dto.KakaoStatusResponse;
import com.api.bizplay_chatbot.kakao.service.KakaoBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Per-bot Kakao integration management. Mounted under
 * {@code /chatbot/api/v1/bots/{id}/kakao} so it sits alongside Telegram's matching
 * config endpoints.
 */
@Slf4j
@Tag(name = "Kakao integration",
        description = "Link a bot to a KakaoTalk channel via Kakao i Openbuilder Skill webhooks")
@RestController
@RequestMapping("/api/v1/bots/{id}/kakao")
@RequiredArgsConstructor
public class KakaoConfigController {

    private final KakaoBotService kakaoBotService;

    @Operation(summary = "Configure Kakao integration for a bot",
            description = "Generates a fresh per-bot webhook secret and returns the full webhook "
                    + "URL operators paste into Kakao i Openbuilder's Skill configuration. The "
                    + "secret is part of the URL path; the URL as a whole is sensitive and must "
                    + "not be shared outside the operator + Openbuilder. Re-calling this endpoint "
                    + "rotates the secret — the old URL stops working immediately.")
    @PostMapping
    public ResponseEntity<ApiResponse<KakaoStatusResponse>> configure(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Valid @RequestBody(required = false) KakaoConfigureRequest req) {
        String botName = req != null ? req.getBotName() : null;
        log.info("POST /chatbot/api/v1/bots/{}/kakao - configuring (botName={})", id, botName);
        return ResponseEntity.ok(ApiResponse.ok(kakaoBotService.configure(id, botName)));
    }

    @Operation(summary = "Get Kakao integration status for a bot",
            description = "Returns whether the bot is linked, the operator-supplied display name, "
                    + "and the current webhook URL (when the public-base-url is configured). The "
                    + "URL contains the per-bot secret — treat the whole response as sensitive.")
    @GetMapping
    public ResponseEntity<ApiResponse<KakaoStatusResponse>> status(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(kakaoBotService.status(id)));
    }

    @Operation(summary = "Remove Kakao integration from a bot",
            description = "Clears the webhook secret, display name, and configured-at timestamp. "
                    + "After this, the previously-distributed webhook URL returns 404. Existing "
                    + "kakao_chats mappings become orphaned but harmless — they'll be recreated "
                    + "if Kakao is re-configured.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<KakaoStatusResponse>> unconfigure(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        log.info("DELETE /chatbot/api/v1/bots/{}/kakao", id);
        return ResponseEntity.ok(ApiResponse.ok(kakaoBotService.unconfigure(id)));
    }
}
