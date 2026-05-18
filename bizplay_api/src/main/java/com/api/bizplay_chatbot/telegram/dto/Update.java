package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

/**
 * Top-level webhook payload from Telegram. Exactly one of the optional fields
 * is populated per call (Telegram convention). We only react to {@code message}
 * and {@code callbackQuery}; other update kinds (edited messages, channel
 * posts, …) are dropped.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Update {
    private long updateId;
    private TelegramMessage message;
    private CallbackQuery callbackQuery;
}
