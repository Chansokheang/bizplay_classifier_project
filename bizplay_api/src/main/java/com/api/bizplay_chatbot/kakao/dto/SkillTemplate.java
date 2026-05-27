package com.api.bizplay_chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Kakao "template" block — the actual content the user sees. Wraps a list
 * of output components (text bubbles, cards, etc.).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillTemplate {
    private List<OutputComponent> outputs;
}
