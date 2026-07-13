package com.api.bizplay_conversational.service.llmSettingsService;

import com.api.bizplay_chatbot.config.ChatClientFactory;
import com.api.bizplay_chatbot.config.LlmProperties;
import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.ConversationalLlmModel;
import com.api.bizplay_conversational.model.request.LlmModelRequest;
import com.api.bizplay_conversational.model.response.LlmModelResponse;
import com.api.bizplay_conversational.repository.ConversationalLlmModelRepo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class LlmModelServiceImple implements LlmModelService {

    private final ConversationalLlmModelRepo modelRepo;
    private final Map<String, ChatClient> chatClientRegistry;
    private final ChatClientFactory chatClientFactory;
    private final LlmProperties llmProperties;
    private final LlmSettingsService llmSettingsService;

    public LlmModelServiceImple(ConversationalLlmModelRepo modelRepo,
                                Map<String, ChatClient> chatClientRegistry,
                                ChatClientFactory chatClientFactory,
                                LlmProperties llmProperties,
                                LlmSettingsService llmSettingsService) {
        this.modelRepo = modelRepo;
        this.chatClientRegistry = chatClientRegistry;
        this.chatClientFactory = chatClientFactory;
        this.llmProperties = llmProperties;
        this.llmSettingsService = llmSettingsService;
    }

    /** Register every enabled DB-managed model into the live registry at startup. */
    @PostConstruct
    void loadFromStore() {
        try {
            int registered = 0;
            for (ConversationalLlmModel m : modelRepo.findAll()) {
                if (m.isEnabled() && registerIntoRegistry(m)) {
                    registered++;
                }
            }
            log.info("Loaded {} DB-managed LLM model(s) into the registry.", registered);
        } catch (RuntimeException e) {
            // Never let a bad row stop startup — the config models remain available.
            log.warn("Could not load DB-managed LLM models: {}", e.getMessage());
        }
    }

    // --- reads -------------------------------------------------------------------

    @Override
    public List<LlmModelResponse> list() {
        List<LlmModelResponse> out = new ArrayList<>();
        // Static config models first (read-only).
        for (LlmProperties.ModelEntry e : llmProperties.getModels()) {
            if (isBlank(e.getName()) || isBlank(e.getBaseUrl())) {
                continue;
            }
            out.add(configToResponse(e));
        }
        // Then DB-managed models.
        for (ConversationalLlmModel m : modelRepo.findAll()) {
            out.add(dbToResponse(m));
        }
        return out;
    }

    @Override
    public LlmModelResponse getByName(String name) {
        String key = require(name, "name");
        ConversationalLlmModel m = modelRepo.findByName(key);
        if (m != null) {
            return dbToResponse(m);
        }
        LlmProperties.ModelEntry cfg = configEntry(key);
        if (cfg != null) {
            return configToResponse(cfg);
        }
        throw new CustomNotFoundException("LLM model not found: " + name);
    }

    // --- mutations ---------------------------------------------------------------

    @Override
    public LlmModelResponse create(LlmModelRequest request) {
        String name = require(request.getName(), "name");
        String baseUrl = require(request.getBaseUrl(), "baseUrl");
        String model = require(request.getModel(), "model");
        if (configEntry(name) != null) {
            throw new IllegalArgumentException("'" + name + "' is a config-defined model and cannot be recreated here.");
        }
        if (modelRepo.findByName(name) != null) {
            throw new DuplicateKeyException("An LLM model named '" + name + "' already exists.");
        }

        ConversationalLlmModel m = new ConversationalLlmModel();
        m.setName(name);
        m.setBaseUrl(baseUrl);
        m.setModel(model);
        m.setLabel(blankToNull(request.getLabel()));
        m.setApiKey(blankToNull(request.getApiKey()));
        m.setAuthScheme(orDefault(request.getAuthScheme(), "bearer"));
        m.setApiKeyHeader(orDefault(request.getApiKeyHeader(), "bearer"));
        m.setCompletionsPath(orDefault(request.getCompletionsPath(), "/chat/completions"));
        m.setTemperature(request.getTemperature() == null ? 0d : request.getTemperature());
        m.setMaxTokens(request.getMaxTokens() == null ? 2048 : request.getMaxTokens());
        m.setEnabled(request.getEnabled() == null || request.getEnabled());

        // Build the client BEFORE persisting so a bad config fails fast without leaving a dead row.
        if (m.isEnabled()) {
            registerIntoRegistry(m);
        }
        modelRepo.insert(m);
        log.info("Created LLM model '{}' (enabled={}).", name, m.isEnabled());
        return dbToResponse(modelRepo.findByName(name));
    }

    @Override
    public LlmModelResponse update(String name, LlmModelRequest request) {
        String key = require(name, "name");
        if (configEntry(key) != null) {
            throw new IllegalArgumentException("'" + key + "' is a config-defined model and is read-only.");
        }
        ConversationalLlmModel existing = modelRepo.findByName(key);
        if (existing == null) {
            throw new CustomNotFoundException("LLM model not found: " + name);
        }

        if (request.getBaseUrl() != null) existing.setBaseUrl(require(request.getBaseUrl(), "baseUrl"));
        if (request.getModel() != null) existing.setModel(require(request.getModel(), "model"));
        if (request.getLabel() != null) existing.setLabel(blankToNull(request.getLabel()));
        // A null apiKey KEEPS the stored key (so the UI can edit without resending the secret).
        if (request.getApiKey() != null) existing.setApiKey(blankToNull(request.getApiKey()));
        if (request.getAuthScheme() != null) existing.setAuthScheme(orDefault(request.getAuthScheme(), "bearer"));
        if (request.getApiKeyHeader() != null) existing.setApiKeyHeader(orDefault(request.getApiKeyHeader(), "bearer"));
        if (request.getCompletionsPath() != null) existing.setCompletionsPath(orDefault(request.getCompletionsPath(), "/chat/completions"));
        if (request.getTemperature() != null) existing.setTemperature(request.getTemperature());
        if (request.getMaxTokens() != null) existing.setMaxTokens(request.getMaxTokens());
        if (request.getEnabled() != null) existing.setEnabled(request.getEnabled());

        if (existing.isEnabled()) {
            registerIntoRegistry(existing); // rebuild + replace the live client
        } else {
            unregister(key); // disabled -> remove from the registry
        }
        modelRepo.update(existing);
        log.info("Updated LLM model '{}' (enabled={}).", key, existing.isEnabled());
        return dbToResponse(modelRepo.findByName(key));
    }

    @Override
    public void delete(String name) {
        String key = require(name, "name");
        if (configEntry(key) != null) {
            throw new IllegalArgumentException("'" + key + "' is a config-defined model and cannot be deleted here.");
        }
        if (modelRepo.findByName(key) == null) {
            throw new CustomNotFoundException("LLM model not found: " + name);
        }
        // If the conversational agents are pinned to this model, clear the override so they don't
        // resolve to a now-missing client.
        String active = llmSettingsService.getSettings().getActiveModel();
        if (key.equals(active)) {
            llmSettingsService.setActiveModel(null);
            log.info("Cleared active conversational model because '{}' was deleted.", key);
        }
        unregister(key);
        modelRepo.deleteByName(key);
        log.info("Deleted LLM model '{}'.", key);
    }

    @Override
    public com.api.bizplay_conversational.model.response.LlmModelTestResponse test(String name) {
        String key = require(name, "name");
        boolean exists = modelRepo.findByName(key) != null || configEntry(key) != null;
        if (!exists) {
            throw new CustomNotFoundException("LLM model not found: " + name);
        }
        ChatClient client = chatClientRegistry.get(key);
        if (client == null) {
            return com.api.bizplay_conversational.model.response.LlmModelTestResponse.builder()
                    .name(key).ok(false)
                    .error("Model is not registered (disabled?). Enable it before testing.")
                    .build();
        }
        long start = System.currentTimeMillis();
        try {
            String reply = client.prompt().user("Reply with the single word: OK").call().content();
            return com.api.bizplay_conversational.model.response.LlmModelTestResponse.builder()
                    .name(key).ok(true)
                    .reply(truncate(reply))
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return com.api.bizplay_conversational.model.response.LlmModelTestResponse.builder()
                    .name(key).ok(false)
                    .error(rootMessage(e))
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "…";
    }

    /** Deepest cause message — the useful part (e.g. "400 - Auth Fail: No apikey provided"). */
    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }

    // --- helpers -----------------------------------------------------------------

    /** Build a client from a DB model and put/replace it in the registry. Returns false on failure. */
    private boolean registerIntoRegistry(ConversationalLlmModel m) {
        try {
            chatClientRegistry.put(m.getName(), chatClientFactory.build(toEntry(m)));
            return true;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Could not build a client for model '" + m.getName() + "': " + e.getMessage());
        }
    }

    private void unregister(String name) {
        chatClientRegistry.remove(name);
    }

    private LlmProperties.ModelEntry toEntry(ConversationalLlmModel m) {
        LlmProperties.ModelEntry e = new LlmProperties.ModelEntry();
        e.setName(m.getName());
        e.setLabel(m.getLabel());
        e.setBaseUrl(m.getBaseUrl());
        e.setApiKey(m.getApiKey());
        e.setAuthScheme(m.getAuthScheme());
        e.setApiKeyHeader(m.getApiKeyHeader());
        e.setCompletionsPath(m.getCompletionsPath());
        e.setModel(m.getModel());
        e.setTemperature(m.getTemperature());
        e.setMaxTokens(m.getMaxTokens());
        return e;
    }

    private LlmProperties.ModelEntry configEntry(String name) {
        for (LlmProperties.ModelEntry e : llmProperties.getModels()) {
            if (name.equals(e.getName())) {
                return e;
            }
        }
        return null;
    }

    private LlmModelResponse dbToResponse(ConversationalLlmModel m) {
        return LlmModelResponse.builder()
                .name(m.getName())
                .label(m.getLabel())
                .baseUrl(m.getBaseUrl())
                .apiKeyMasked(mask(m.getApiKey()))
                .authScheme(m.getAuthScheme())
                .apiKeyHeader(m.getApiKeyHeader())
                .completionsPath(m.getCompletionsPath())
                .model(m.getModel())
                .temperature(m.getTemperature())
                .maxTokens(m.getMaxTokens())
                .enabled(m.isEnabled())
                .registered(chatClientRegistry.containsKey(m.getName()))
                .source("DB")
                .createdAt(m.getCreatedDate())
                .updatedAt(m.getUpdatedDate())
                .build();
    }

    private LlmModelResponse configToResponse(LlmProperties.ModelEntry e) {
        return LlmModelResponse.builder()
                .name(e.getName())
                .label(e.getLabel())
                .baseUrl(e.getBaseUrl())
                .apiKeyMasked(mask(e.getApiKey()))
                .authScheme(e.getAuthScheme())
                .apiKeyHeader(e.getApiKeyHeader())
                .completionsPath(e.getCompletionsPath())
                .model(e.getModel())
                .temperature(e.getTemperature())
                .maxTokens(e.getMaxTokens())
                .enabled(true)
                .registered(chatClientRegistry.containsKey(e.getName()))
                .source("CONFIG")
                .build();
    }

    /** Show only the first/last few characters of a secret; never the whole key. */
    private static String mask(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim();
        if (k.length() <= 8) {
            return "****";
        }
        return k.substring(0, 4) + "…" + k.substring(k.length() - 4);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String orDefault(String v, String def) {
        return isBlank(v) ? def : v.trim();
    }

    private static String require(String v, String field) {
        if (isBlank(v)) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return v.trim();
    }
}
