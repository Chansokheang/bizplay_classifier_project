package com.api.bizplay_conversational.service.llmSettingsService;

import com.api.bizplay_conversational.model.entity.ConversationalLlmSetting;
import com.api.bizplay_conversational.model.response.LlmSettingsResponse;
import com.api.bizplay_conversational.repository.ConversationalLlmSettingRepo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmSettingsServiceImple implements LlmSettingsService {

    private final ConversationalLlmSettingRepo settingRepo;
    /** Registered ChatClients keyed by model name — the set of selectable models. */
    private final Map<String, ChatClient> chatClientRegistry;

    /** In-memory cache of the active override, so resolve() never hits the DB. Blank = no override. */
    private volatile String activeModel;

    public LlmSettingsServiceImple(ConversationalLlmSettingRepo settingRepo,
                                   Map<String, ChatClient> chatClientRegistry) {
        this.settingRepo = settingRepo;
        this.chatClientRegistry = chatClientRegistry;
    }

    /** Load the persisted selection once at startup into the cache. */
    @PostConstruct
    void loadFromStore() {
        try {
            ConversationalLlmSetting row = settingRepo.findSingleton();
            this.activeModel = row == null ? null : blankToNull(row.getActiveModel());
            log.info("Conversational LLM override loaded: {}", activeModel == null ? "(none)" : activeModel);
        } catch (RuntimeException e) {
            // Never let a bad/absent settings row stop the app — agents fall back to their defaults.
            log.warn("Could not load conversational LLM setting; using per-agent defaults: {}", e.getMessage());
            this.activeModel = null;
        }
    }

    @Override
    public String resolve(String agentDefaultModel) {
        String override = this.activeModel;
        return (override == null || override.isBlank()) ? agentDefaultModel : override;
    }

    @Override
    public LlmSettingsResponse getSettings() {
        ConversationalLlmSetting row = settingRepo.findSingleton();
        return LlmSettingsResponse.builder()
                .activeModel(this.activeModel)
                .availableModels(availableModels())
                .updatedAt(row == null ? null : row.getUpdatedDate())
                .build();
    }

    @Override
    public LlmSettingsResponse setActiveModel(String model) {
        String requested = blankToNull(model);
        if (requested != null && !chatClientRegistry.containsKey(requested)) {
            throw new IllegalArgumentException(
                    "Unknown model '" + requested + "'. Available: " + availableModels() + ".");
        }
        settingRepo.upsertActiveModel(requested); // null clears the override
        this.activeModel = requested;
        log.info("Conversational LLM override set to: {}", requested == null ? "(cleared)" : requested);
        ConversationalLlmSetting row = settingRepo.findSingleton();
        return LlmSettingsResponse.builder()
                .activeModel(requested)
                .availableModels(availableModels())
                .updatedAt(row == null ? LocalDateTime.now() : row.getUpdatedDate())
                .build();
    }

    private List<String> availableModels() {
        return new ArrayList<>(chatClientRegistry.keySet());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
