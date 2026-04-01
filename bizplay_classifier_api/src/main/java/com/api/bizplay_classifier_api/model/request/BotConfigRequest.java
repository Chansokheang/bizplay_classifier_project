package com.api.bizplay_classifier_api.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BotConfigRequest {
    @NotNull(message = "Company Id can not be null.")
    private UUID companyId;

    @NotNull(message = "Config can not be null.")
    @Valid
    private Config config;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Config {
        @NotBlank(message = "Model name can not be blank.")
        private String modelName;

        @NotNull(message = "Temperature can not be null.")
        @DecimalMin(value = "0.0", message = "Temperature must be >= 0.0.")
        @DecimalMax(value = "2.0", message = "Temperature must be <= 2.0.")
        private Double temperature;

        @NotBlank(message = "System prompt can not be blank.")
        private String systemPrompt;
    }
}
