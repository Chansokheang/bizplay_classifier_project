package com.api.bizplay_classifier_api.service.botConfigService;

import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;

public interface BotConfigService {
    BotConfigDTO createBotConfig(BotConfigRequest botConfigRequest);
}

