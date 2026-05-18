package com.api.bizplay_compliance.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record LocationAnomalyCheckRequest(
        @Schema(
                description = "Address to resolve with the Naver geocode API.",
                example = "충북 청주시 서원구 충대로 1 충북대학교",
                defaultValue = "충북 청주시 서원구 충대로 1 충북대학교"
        )
        String address
) {
}
