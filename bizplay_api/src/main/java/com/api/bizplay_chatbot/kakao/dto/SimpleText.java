package com.api.bizplay_chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kakao "simpleText" output component — a plain text bubble. The smallest
 * useful response shape: one of these inside a {@code template.outputs[]}
 * array is what a user sees as a bot message.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimpleText {
    private String text;
}
