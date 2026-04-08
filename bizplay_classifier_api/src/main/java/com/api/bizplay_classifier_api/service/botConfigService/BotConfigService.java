package com.api.bizplay_classifier_api.service.botConfigService;

import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.model.request.PromptEnhancementRequest;
import com.api.bizplay_classifier_api.model.response.PromptEnhancementResponse;

import java.util.UUID;

public interface BotConfigService {
    BotConfigDTO createBotConfig(BotConfigRequest botConfigRequest);

    BotConfigDTO upsertBotConfig(UUID companyId, BotConfigRequest.Config config);

    String updatePromptFromLatestTrainingData(UUID companyId, Integer sampleRows);

    PromptEnhancementResponse updatePromptFromLatestTrainingDataWithSource(UUID companyId, Integer sampleRows);

    PromptEnhancementResponse updatePromptFromLatestTrainingDataWithSource(UUID companyId, Integer sampleRows, PromptEnhancementRequest request);

    PromptEnhancementResponse generatePromptEnhancementPreview(UUID companyId, Integer sampleRows);

    BotConfigDTO getLatestBotConfigByCompanyId(UUID companyId);
}
