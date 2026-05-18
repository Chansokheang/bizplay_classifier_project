package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Telegram inline keyboard markup — a 2-D array of buttons rendered under a
 * message. Used here to expose recommended questions as tappable chips after
 * /start.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InlineKeyboardMarkup {
    private List<List<InlineKeyboardButton>> inlineKeyboard;
}
