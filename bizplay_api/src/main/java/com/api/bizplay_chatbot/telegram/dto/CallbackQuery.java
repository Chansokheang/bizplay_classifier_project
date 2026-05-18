package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

/** Inline-keyboard button press event. We attach the question text in
 *  {@code data} when building the recommended-question keyboard, so when the
 *  user taps a chip we receive the question to forward to ChatService. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CallbackQuery {
    private String id;
    private TelegramUser from;
    private TelegramMessage message;
    private String chatInstance;
    private String data;
}
