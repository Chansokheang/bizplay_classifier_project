package com.api.bizplay_conversational.model.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Set the active conversational LLM. {@code model} must be a registered model name (see the
 * availableModels in the GET response). Pass null or an empty string to CLEAR the override and let
 * each sub-agent fall back to its own configured default.
 */
@Getter
@Setter
public class LlmSettingsUpdateRequest {
    private String model;
}
