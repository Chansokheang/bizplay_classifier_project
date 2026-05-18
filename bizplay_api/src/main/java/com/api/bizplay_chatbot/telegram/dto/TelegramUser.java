package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

/** Telegram User object. Used here for getMe (returns the bot's own user)
 *  and for from-fields on inbound messages. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TelegramUser {
    private long id;
    /** Lombok generates {@code isBot()}/{@code setBot(boolean)} for this
     *  field, so Jackson's bean introspection picks up the property as
     *  {@code "bot"} — which would map to JSON key {@code "bot"} under the
     *  snake_case strategy and miss Telegram's actual {@code "is_bot"} key.
     *  The explicit {@link JsonProperty} pins both directions to the wire
     *  name so {@code me.isBot()} reflects the real value. */
    @JsonProperty("is_bot")
    private boolean isBot;
    private String firstName;
    private String lastName;
    private String username;
    private String languageCode;
}
