package com.api.bizplay_classifier_api.model.request;

import com.api.bizplay_classifier_api.model.enums.AiProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BotConfigRequest {
    @NotNull(message = "Company Id can not be null.")
    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID companyId;

    @NotNull(message = "Config can not be null.")
    @Valid
    private Config config;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Schema(name = "BotConfig")
    public static class Config {
        @NotNull(message = "Provider can not be null.")
        @Schema(example = "OPENAI_COMPATIBLE", defaultValue = "OPENAI_COMPATIBLE")
        private AiProvider provider;

        @NotBlank(message = "Model name can not be blank.")
        @Schema(
                example = "EXAONE-3.5-7.8B-Instruct-AWQ",
                defaultValue = "EXAONE-3.5-7.8B-Instruct-AWQ"
        )
        private String modelName;

        @NotNull(message = "Temperature can not be null.")
        @DecimalMin(value = "0.0", message = "Temperature must be >= 0.0.")
        @DecimalMax(value = "2.0", message = "Temperature must be <= 2.0.")
        @Schema(example = "0.0", defaultValue = "0.0")
        private Double temperature;

        @Schema(
                example = "sk-d7a20eb034c847e8994e192b40c69a61",
                defaultValue = "sk-d7a20eb034c847e8994e192b40c69a61"
        )
        private String apiKey;

        @NotBlank(message = "System prompt can not be blank.")
        @Schema(
                example = "당신은 회사의 지출 분류를 담당하는 지능형 비용 분류 도우미입니다. 당신의 역할은 거래 내역을 분석하고, 제공된 규칙과 카테고리에 따라 분류하는 것입니다.\n\n거래가 주어지면 가장 적절한 카테고리와 규칙을 식별하고, 신뢰도 점수와 간단한 근거를 함께 제시하세요.\n\n항상 간결하고 정확하게 답변하세요.",
                defaultValue = "당신은 회사의 지출 분류를 담당하는 지능형 비용 분류 도우미입니다. 당신의 역할은 거래 내역을 분석하고, 제공된 규칙과 카테고리에 따라 분류하는 것입니다.\n\n거래가 주어지면 가장 적절한 카테고리와 규칙을 식별하고, 신뢰도 점수와 간단한 근거를 함께 제시하세요.\n\n항상 간결하고 정확하게 답변하세요."
        )
        private String systemPrompt;
    }
}
