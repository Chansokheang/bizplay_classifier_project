package com.api.bizplay_classifier_api.service.botConfigService;

import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.enums.AiProvider;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.model.request.PromptEnhancementRequest;
import com.api.bizplay_classifier_api.model.response.PromptEnhancementResponse;

import java.util.concurrent.CompletableFuture;

public interface BotConfigService {
    BotConfigDTO createBotConfig(BotConfigRequest botConfigRequest, AiProvider provider, String modelName);

    BotConfigDTO upsertBotConfig(String companyId, BotConfigRequest.Config config, AiProvider provider, String modelName);

    String updatePromptFromLatestTrainingData(String companyId, Integer sampleRows);

    PromptEnhancementResponse updatePromptFromLatestTrainingDataWithSource(String companyId, Integer sampleRows);

    PromptEnhancementResponse updatePromptFromLatestTrainingDataWithSource(String companyId, Integer sampleRows, PromptEnhancementRequest request);

    PromptEnhancementResponse generatePromptEnhancementPreview(String companyId, Integer sampleRows);

    CompletableFuture<PromptEnhancementResponse> generatePromptEnhancementPreviewAsync(String companyId, Integer sampleRows);

    BotConfigDTO getLatestBotConfigByCompanyId(String companyId);
}
