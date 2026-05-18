package com.api.bizplay_chatbot.telegram.client;

import com.api.bizplay_chatbot.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a call to the Telegram Bot API fails — either because the HTTP
 * call itself errored (network, timeout, 5xx) or because Telegram returned
 * {@code ok:false} in the response envelope (e.g. invalid token, chat not
 * found, message too long).
 *
 * Defaults to HTTP 502 ("bad gateway") because most failures here are
 * upstream-Telegram problems and 502 is the right signal to operators
 * scanning logs. Caller code can override the status when the failure is
 * really a 4xx-class user error (e.g. invalid token on configure → 400).
 */
public class TelegramApiException extends BusinessException {

    public TelegramApiException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public TelegramApiException(String message, HttpStatus status) {
        super(message, status);
    }
}
