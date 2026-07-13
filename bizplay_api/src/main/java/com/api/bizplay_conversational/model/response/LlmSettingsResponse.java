package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Current conversational-LLM selection. {@code activeModel} null means no override is set — each
 * sub-agent uses its own configured default. {@code availableModels} lists every registered model
 * name that can be selected.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmSettingsResponse {
    private String activeModel;
    private List<String> availableModels;
    private LocalDateTime updatedAt;
}
