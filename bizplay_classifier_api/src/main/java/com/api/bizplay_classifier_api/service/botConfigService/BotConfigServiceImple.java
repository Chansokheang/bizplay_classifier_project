package com.api.bizplay_classifier_api.service.botConfigService;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.repository.BotConfigRepo;
import com.api.bizplay_classifier_api.utils.GetCurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@AllArgsConstructor
public class BotConfigServiceImple implements BotConfigService {

    private final BotConfigRepo botConfigRepo;
    private final GetCurrentUser getCurrentUser;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BotConfigDTO createBotConfig(BotConfigRequest botConfigRequest) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = botConfigRepo.existsCompanyByIdAndUserId(botConfigRequest.getCompanyId(), currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + botConfigRequest.getCompanyId());
        }

        String configJson = toConfigJson(botConfigRequest);
        return botConfigRepo.createBotConfig(
                BotConfigRequest.builder()
                        .companyId(botConfigRequest.getCompanyId())
                        .config(botConfigRequest.getConfig())
                        .build(),
                configJson
        );
    }

    private String toConfigJson(BotConfigRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getConfig());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize config.");
        }
    }
}
