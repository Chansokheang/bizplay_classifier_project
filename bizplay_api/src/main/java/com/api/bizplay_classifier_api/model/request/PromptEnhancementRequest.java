package com.api.bizplay_classifier_api.model.request;

import com.api.bizplay_classifier_api.model.enums.AiProvider;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromptEnhancementRequest {
    private AiProvider provider;

    private String modelName;

    private String apiKey;

    @DecimalMin(value = "0.0", message = "Temperature must be >= 0.0.")
    @DecimalMax(value = "2.0", message = "Temperature must be <= 2.0.")
    private Double temperature;
}
