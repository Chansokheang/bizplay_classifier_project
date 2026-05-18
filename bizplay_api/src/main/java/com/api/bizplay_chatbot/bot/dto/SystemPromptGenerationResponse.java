package com.api.bizplay_chatbot.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemPromptGenerationResponse {

    /** The generated system prompt — paste into BotCreateRequest.systemPrompt
     *  (or edit further before submitting). */
    private String systemPrompt;

    /** Which model produced it, for caller transparency. */
    private String llmModel;
}
