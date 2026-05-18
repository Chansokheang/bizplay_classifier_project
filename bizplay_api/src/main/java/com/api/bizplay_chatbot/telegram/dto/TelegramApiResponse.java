package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

/**
 * Envelope every Telegram Bot API response is wrapped in:
 * {@code { ok: true, result: {...} }} on success or
 * {@code { ok: false, error_code: 400, description: "..." }} on error.
 *
 * Generic {@code <T>} is the result payload type for the specific call.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TelegramApiResponse<T> {
    private boolean ok;
    private T result;
    private Integer errorCode;
    private String description;
}
