package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload for updating a bot. Every field is optional (PATCH semantics) — null
 * means "leave unchanged". Recommended questions are an exception: when the
 * field is non-null the entire collection is replaced atomically; when null
 * the existing list is left as-is.
 */
@Data
public class BotUpdateRequest {

    @Size(max = 255)
    private String name;

    private String description;

    @Email
    @Size(max = 255)
    private String contactEmail;

    @Size(max = 50)
    private String contactPhone;

    private String systemPrompt;

    private Boolean sourceExpose;

    @Size(max = 100)
    private String llmModel;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal llmTemperature;

    @Min(64)
    @Max(8192)
    private Integer maxAnswerLength;

    @Min(0)
    @Max(20)
    private Integer historyTurns;

    @Min(1)
    @Max(50)
    private Integer topK;

    @Schema(description = "When provided, replaces the bot's existing recommended questions. "
            + "Pass an empty list to clear them. Pass null (or omit) to leave them unchanged.")
    @Valid
    private List<RecommendedQuestionDto> recommendedQuestions;
}
