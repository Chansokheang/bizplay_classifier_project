package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

/** Inbound Telegram Message. Only the fields we read are mapped. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TelegramMessage {
    private long messageId;
    private TelegramUser from;
    private TelegramChat chat;
    private long date;
    private String text;
}
