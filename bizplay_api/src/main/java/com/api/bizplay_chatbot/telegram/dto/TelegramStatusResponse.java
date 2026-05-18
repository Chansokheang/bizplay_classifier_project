package com.api.bizplay_chatbot.telegram.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Telegram-integration view of a bot. Returned by configure / status
 * endpoints. Crucially, it never carries the raw bot token — only flags and
 * non-secret display strings.
 *
 * <p>With long polling there is no inbound webhook URL to report; operators
 * verifying integration health can either inspect this endpoint (configured
 * flag + username) or call Telegram's getWebhookInfo directly to confirm no
 * stale webhook is registered for the token.
 */
@Getter
@Builder
public class TelegramStatusResponse {
    private boolean telegramConfigured;
    private String botUsername;
    private LocalDateTime configuredAt;
}
