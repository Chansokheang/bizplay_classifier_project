package com.api.bizplay_classifier_api.model.request;

import com.api.bizplay_classifier_api.model.enums.AiProvider;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BotConfigRequest {
    @NotNull(message = "Company Id can not be null.")
    @JsonProperty("corpNo")
    @JsonAlias("companyId")
    @Schema(name = "corpNo", example = "1234567890")
    private String corpNo;

    @NotNull(message = "Config can not be null.")
    @Valid
    private Config config;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Schema(name = "BotConfig")
    public static class Config {
        @Schema(
                example = "EXAONE",
                defaultValue = "EXAONE",
                allowableValues = {"EXAONE", "OPENAI", "GEMINI", "CLAUDE"},
                accessMode = Schema.AccessMode.READ_ONLY
        )
        private AiProvider provider;

        @Schema(
                example = "EXAONE-3.5-7.8B-Instruct-AWQ",
                defaultValue = "EXAONE-3.5-7.8B-Instruct-AWQ",
                allowableValues = {
                        "EXAONE-3.5-7.8B-Instruct-AWQ",
                        "gpt-4o-mini",
                        "gemini-1.5-flash",
                        "claude-3-5-sonnet-latest"
                },
                accessMode = Schema.AccessMode.READ_ONLY
        )
        private String modelName;

        @NotNull(message = "Temperature can not be null.")
        @DecimalMin(value = "0.0", message = "Temperature must be >= 0.0.")
        @DecimalMax(value = "2.0", message = "Temperature must be <= 2.0.")
        @Schema(example = "0.0", defaultValue = "0.0")
        private Double temperature;

        @Schema(
                example = "sk-d7a20eb034c847e8994e192b40c69a61",
                defaultValue = "sk-d7a20eb034c847e8994e192b40c69a61"
        )
        private String apiKey;

        @NotBlank(message = "System prompt can not be blank.")
        @Schema(
                example = "You are a corporate expense classification assistant. Use only the provided account list and examples. Return concise and accurate guidance.",
                defaultValue = "You are a corporate expense classification assistant. Use only the provided account list and examples. Return concise and accurate guidance."
        )
        private String systemPrompt;
    }
}
