package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedQuestionDto {

    @Schema(description = "Server-assigned ID; ignored on create/update",
            accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @Schema(description = "The canned question text shown to end users",
            example = "When can I claim travel expenses?")
    @NotBlank
    @Size(max = 500)
    private String question;
}
