package com.api.bizplay_chatbot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One button on an inline keyboard. We use {@code text} (visible label) and
 * {@code callback_data} (echoed back in {@link CallbackQuery#getData()} when
 * tapped) to surface recommended questions on /start.
 *
 * Telegram's API also accepts {@code url}, {@code switch_inline_query},
 * etc., but {@code callback_data} is sufficient here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InlineKeyboardButton {
    private String text;
    private String callbackData;
}
