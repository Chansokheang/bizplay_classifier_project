package com.api.bizplay_chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the app.llm.* config supporting multiple LLM models
 * served on different servers/ports.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** Name of the default model (must match one entry in the models list) */
    private String defaultModel;

    /** List of available LLM model configurations */
    private List<ModelEntry> models = new ArrayList<>();

    @Getter
    @Setter
    public static class ModelEntry {
        /** Unique identifier used in API requests and UI selector */
        private String name;
        /** Display label for the UI (e.g., "EXAONE-3.5-7.8B (Fast)") */
        private String label;
        /** vLLM server base URL (e.g., http://localhost:8000/v1) */
        private String baseUrl;
        /** API key for authentication */
        private String apiKey = "local";
        /** Model name as registered in vLLM */
        private String model;
        /** Sampling temperature */
        private double temperature = 0;
        /** Max output tokens */
        private int maxTokens = 1024;
    }
}
