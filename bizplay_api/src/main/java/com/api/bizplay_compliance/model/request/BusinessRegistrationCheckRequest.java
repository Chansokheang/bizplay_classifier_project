package com.api.bizplay_compliance.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record BusinessRegistrationCheckRequest(
        @Schema(
                description = "Business registration number to look up in the NTS businessman status API.",
                example = "3158300467",
                defaultValue = "3158300467"
        )
        String businessNumber
) {
}
