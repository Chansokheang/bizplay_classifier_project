package com.api.bizplay_classifier_api.service.botConfigService;

import com.api.bizplay_classifier_api.model.enums.AiProvider;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;

public final class BotConfigDefaults {
    public static final AiProvider DEFAULT_PROVIDER = AiProvider.EXAONE;
    public static final String DEFAULT_MODEL_NAME = "EXAONE-3.5-7.8B-Instruct-AWQ";
    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final String DEFAULT_API_KEY = "sk-d7a20eb034c847e8994e192b40c69a61";
    public static final String DEFAULT_SYSTEM_PROMPT = """
            당신은 회사의 지출 분류를 담당하는 지능형 비용 분류 도우미입니다. 당신의 역할은 거래 내역을 분석하고, 제공된 규칙과 카테고리에 따라 분류하는 것입니다.

            거래가 주어지면 가장 적절한 카테고리와 규칙을 식별하고, 신뢰도 점수와 간단한 근거를 함께 제시하세요.

            항상 간결하고 정확하게 답변하세요.
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
