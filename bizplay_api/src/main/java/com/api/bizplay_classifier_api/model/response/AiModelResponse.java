package com.api.bizplay_classifier_api.model.response;

import com.api.bizplay_classifier_api.model.enums.AiProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelResponse {
    private AiProvider provider;
    private String modelName;
}
