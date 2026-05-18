package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload for creating a new bot. All LLM fields except {@code llmModel} fall
 * back to BotDefaults / sensible numeric defaults if omitted, so a minimal
 * caller can supply just {name, llmModel}.
 */
@Data
public class BotCreateRequest {

    @Schema(description = "Owning corporation, identified by its natural business code (corp_no). Soft "
            + "reference to a corp managed by the external login service — any value is accepted, no local "
            + "existence check. Defaults to the configured default tenant (corp_no=DEFAULT in dev) if omitted.",
            example = "ACME-001")
    @Size(max = 50)
    private String corpNo;

    @Schema(description = "Display name", example = "Travel Expense Bot")
    @NotBlank
    @Size(max = 255)
    private String name;

    @Schema(description = "Free-form description of what this bot is for")
    private String description;

    @Schema(example = "travel-bot-support@bizplay.com")
    @Email
    @Size(max = 255)
    private String contactEmail;

    @Schema(example = "+82-2-1234-5678")
    @Size(max = 50)
    private String contactPhone;

    @Schema(description = "System prompt that frames every chat with this bot. "
            + "If omitted, a sensible default is used (see BotDefaults).")
    private String systemPrompt;

    @Schema(description = "Whether sources are returned to clients. Defaults to true.")
    private Boolean sourceExpose;

    @Schema(description = "LLM registry name (see GET /chatbot/api/v1/rag/chat/models for valid values)",
            example = "exaone-3.5-7.8b")
    @NotBlank
    @Size(max = 100)
    private String llmModel;

    @Schema(description = "0.0–1.0. Defaults to 0.0 (deterministic).",
            example = "0", defaultValue = "0")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal llmTemperature;

    @Schema(description = "Maximum tokens in the LLM's reply. Defaults to 1024.",
            example = "1024", defaultValue = "1024")
    @Min(64)
    @Max(8192)
    private Integer maxAnswerLength;

    @Schema(description = "How many prior turns of conversation are included in the prompt. Defaults to 5.",
            example = "5", defaultValue = "5")
    @Min(0)
    @Max(20)
    private Integer historyTurns;

    @Schema(description = "Number of chunks retrieved per chat. Defaults to 5.",
            example = "5", defaultValue = "5")
    @Min(1)
    @Max(50)
    private Integer topK;

    @Schema(description = "Optional pre-canned questions to seed the bot with")
    @Valid
    private List<RecommendedQuestionDto> recommendedQuestions;
}
