package com.api.bizplay_classifier_api.service.aiFallbackService;

import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;

import java.util.List;
import java.util.Map;

public interface AiFallbackService {
    AiFallbackResult classify(Map<String, String> rowData, List<RuleClassifierDTO> classifiers, String promptTemplate);

    String generatePrompt(List<Map<String, String>> trainingRows);

    record AiFallbackResult(String usageCode, String usageName, String reason) {
    }
}
