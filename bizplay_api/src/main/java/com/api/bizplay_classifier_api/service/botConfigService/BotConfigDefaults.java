package com.api.bizplay_classifier_api.service.botConfigService;

import com.api.bizplay_classifier_api.model.enums.AiProvider;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;

public final class BotConfigDefaults {
    public static final AiProvider DEFAULT_PROVIDER = AiProvider.EXAONE;
    public static final String DEFAULT_MODEL_NAME = "exaone-357-8b-instruct-awq";
    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final String DEFAULT_API_KEY = "sk-d7a20eb034c847e8994e192b40c69a61";
    public static final String DEFAULT_SYSTEM_PROMPT = """
            You are a corporate expense classification assistant.

            Analyze each transaction and classify it using only the company's allowed 용도 list
            (identified by 용도코드 and 용도명). Do not use the term "계정과목" — refer to the
            classification target as "용도".

            Prefer concrete evidence such as merchant name, merchant industry, and learned examples.

            Return concise, accurate classification guidance.
            """;

    private BotConfigDefaults() {
    }

    public static BotConfigRequest.Config defaultConfig() {
        return BotConfigRequest.Config.builder()
                .provider(DEFAULT_PROVIDER)
                .modelName(DEFAULT_MODEL_NAME)
                .temperature(DEFAULT_TEMPERATURE)
                .apiKey(DEFAULT_API_KEY)
                .systemPrompt(DEFAULT_SYSTEM_PROMPT)
                .build();
    }
}
