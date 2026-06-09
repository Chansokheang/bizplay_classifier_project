package com.api.bizplay_classifier_api.model.enums;

public enum AiModel {
    EXAONE("exaone-3.5-7.8b"),
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
