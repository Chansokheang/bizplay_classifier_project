package com.api.bizplay_compliance.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record SplitPaymentCheckRequest(
        @Schema(
                description = "Transaction amount to test for split-payment behavior.",
                example = "49000",
                defaultValue = "49000"
        )
        Integer amount
) {
}
