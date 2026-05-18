package com.api.bizplay_classifier_api.service.aiFallbackService;

import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;

import java.util.List;
import java.util.Map;

public interface AiFallbackService {
    AiFallbackResult classify(
            Map<String, String> rowData,
            List<RuleClassifierDTO> classifiers,
            String promptTemplate,
            BotConfigRequest.Config aiConfig
    );

    String generatePrompt(List<Map<String, String>> trainingRows, BotConfigRequest.Config aiConfig);

    record AiFallbackResult(String usageCode, String usageName, String reason) {
    }
}
