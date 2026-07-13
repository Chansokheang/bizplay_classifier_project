package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A runtime-managed LLM model definition (persisted, editable via the /llm-models endpoints). Mirrors
 * the fields of a static {@code app.llm.models[*]} config entry; loaded into the ChatClient registry
 * at startup and on each change. {@code name} is the registry key referenced by
 * {@code app.conversational.*.model} and the active-model setting.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConversationalLlmModel {

    private String name;
    private String label;
    private String baseUrl;
    private String apiKey;
    private String authScheme;
    private String apiKeyHeader;
    private String completionsPath;
    private String model;
    private double temperature;
    private int maxTokens;
    private boolean enabled;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
