package com.api.bizplay_chatbot.telegram.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_chatbot.telegram.dto.TelegramConfigureRequest;
import com.api.bizplay_chatbot.telegram.dto.TelegramStatusResponse;
import com.api.bizplay_chatbot.telegram.service.TelegramBotService;
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
 * Per-bot Telegram integration management. Mounted under {@code /chatbot/api/v1/bots/{id}/telegram}
 * so it sits alongside the existing bot-config endpoints in {@code BotController}.
 */
@Slf4j
@Tag(name = "Telegram integration",
        description = "Link a bot to a Telegram bot so external Telegram users can chat with it")
@RestController
@RequestMapping("/api/v1/bots/{id}/telegram")
@RequiredArgsConstructor
public class TelegramConfigController {

    private final TelegramBotService telegramBotService;

    @Operation(summary = "Configure Telegram integration for a bot",
            description = "Validates the supplied bot token via Telegram's getMe and starts a "
                    + "background long-polling thread that pulls updates from Telegram on behalf "
                    + "of this bot. No inbound URL is required — the integration works behind any "
                    + "firewall as long as outbound HTTPS to api.telegram.org is allowed. Any prior "
                    + "webhook on the token is dropped, and Telegram's backlog of queued updates is "
                    + "discarded so the first poll cycle returns only fresh messages. The token is "
                    + "stored on the bot row but never returned in any response — only the bot's "
                    + "Telegram-side username is echoed back. Returns 400 if the token is invalid, "
                    + "404 if the bot doesn't exist, 409 if the token is already linked to another "
                    + "bot in this system.")
    @PostMapping
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> configure(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Valid @RequestBody TelegramConfigureRequest req) {
        // Token never logged.
        log.info("POST /chatbot/api/v1/bots/{}/telegram - configuring", id);
        return ResponseEntity.ok(ApiResponse.ok(telegramBotService.configure(id, req.getToken())));
    }

    @Operation(summary = "Get Telegram integration status for a bot",
            description = "Returns whether the bot is linked and the linked Telegram username. "
                    + "Useful for the bot-management UI to show 'Configured ✓' vs 'Not configured'. "
                    + "Token is never returned.")
    @GetMapping
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> status(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(telegramBotService.status(id)));
    }

    @Operation(summary = "Remove Telegram integration from a bot",
            description = "Stops the bot's polling thread, drops any residual webhook on Telegram's "
                    + "side (best-effort — local cleanup proceeds even if Telegram is unreachable), "
                    + "and clears the token, username, and offset from the bot row. The bot itself "
                    + "remains; this only severs the Telegram link. Existing telegram_chats "
                    + "mappings will become orphaned but harmless — they'll be recreated if "
                    + "Telegram is re-configured.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> unconfigure(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        log.info("DELETE /chatbot/api/v1/bots/{}/telegram", id);
        return ResponseEntity.ok(ApiResponse.ok(telegramBotService.unconfigure(id)));
    }
}
