package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

/** Telegram Chat object. {@code type} is one of "private", "group",
 *  "supergroup", "channel" — the webhook handler short-circuits anything
 *  other than "private" since this V1 only supports 1-on-1 chats. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TelegramChat {
    private long id;
    private String type;
    private String title;
    private String username;
    private String firstName;
    private String lastName;
}
