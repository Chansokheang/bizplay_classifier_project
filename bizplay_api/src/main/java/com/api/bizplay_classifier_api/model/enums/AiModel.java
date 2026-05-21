package com.api.bizplay_classifier_api.model.enums;

public enum AiModel {
    EXAONE("exaone-357-8b-instruct-awq"),
    OPENAI("gpt-4o-mini"),
    GEMINI("gemini-1.5-flash"),
    CLAUDE("claude-3-5-sonnet-latest");

    private final String modelName;

    AiModel(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }
}
