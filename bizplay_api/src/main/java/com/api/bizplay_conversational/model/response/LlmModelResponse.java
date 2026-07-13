package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * A model in the registry. The API key is never returned in full — only a masked hint
 * ({@code apiKeyMasked}, e.g. "U2Fs…W8k0"). {@code source} is DB for runtime-managed models
 * (editable) or CONFIG for static app.llm.models entries (read-only). {@code registered} indicates
 * whether the model is currently live in the ChatClient registry (selectable by agents).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmModelResponse {
    private String name;
    private String label;
    private String baseUrl;
    private String apiKeyMasked;
    private String authScheme;
    private String apiKeyHeader;
    private String completionsPath;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private boolean enabled;
    private boolean registered;
    /** "DB" (managed here) or "CONFIG" (from app.llm.models, read-only). */
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
