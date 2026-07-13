package com.api.bizplay_chatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
public class SpringAiConfig {

    // System prompts moved to per-bot config — see BotDefaults.DEFAULT_SYSTEM_PROMPT
    // for the seed value used when a bot is created without one. ChatService
    // applies the bot's prompt via .system(...) on each call.

    /**
     * Registry of ChatClient instances keyed by model name — seeded from app.llm.models config, then
     * augmented at startup with DB-managed models and mutated at runtime by the model-management
     * endpoints. A {@link ConcurrentHashMap} so those runtime add/update/remove operations are safe
     * while agents read from it concurrently.
     */
    @Bean
    public Map<String, ChatClient> chatClientRegistry(LlmProperties llmProperties, ChatClientFactory chatClientFactory) {
        Map<String, ChatClient> registry = new ConcurrentHashMap<>();

        for (LlmProperties.ModelEntry entry : llmProperties.getModels()) {
            // Skip entries with empty name or base-url (unused model slots from .env)
            if (entry.getName() == null || entry.getName().isBlank()
                    || entry.getBaseUrl() == null || entry.getBaseUrl().isBlank()) {
                continue;
            }
            registry.put(entry.getName(), chatClientFactory.build(entry));
            log.info("Registered LLM model: name={}, label={}, baseUrl={}, model={}",
                    entry.getName(), entry.getLabel(), entry.getBaseUrl(), entry.getModel());
        }

        if (registry.isEmpty()) {
            throw new IllegalStateException("No LLM models configured in app.llm.models");
        }

        String defaultModel = llmProperties.getDefaultModel();
        if (defaultModel != null && !registry.containsKey(defaultModel)) {
            throw new IllegalStateException("Default model '" + defaultModel
                    + "' not found in configured models: " + registry.keySet());
        }

        log.info("LLM model registry ready: {} config model(s), default={}",
                registry.size(), defaultModel);
        return registry;
    }

    /**
     * Manually configured embedding model. Uses a separate OpenAiApi instance
     * pointing to the embedding server (BAAI/bge-m3 via vLLM).
     */
    @Bean
    public OpenAiEmbeddingModel embeddingModel(
            @Value("${app.embed.base-url}") String baseUrl,
            @Value("${app.embed.api-key}") String apiKey,
            @Value("${app.embed.model}") String model) {

        log.info("Configuring embedding model: baseUrl={}, model={}", baseUrl, model);

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        RestClient.Builder embeddingRestClient = RestClient.builder()
                .requestFactory(requestFactory);

        OpenAiApi embeddingApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(embeddingRestClient)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        return new OpenAiEmbeddingModel(embeddingApi, MetadataMode.EMBED, options);
    }
}
