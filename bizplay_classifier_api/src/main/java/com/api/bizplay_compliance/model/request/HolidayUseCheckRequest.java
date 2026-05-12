package com.api.bizplay_compliance.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record HolidayUseCheckRequest(
        @Schema(
                description = "Date to check against weekend and public holiday rules.",
                example = "2026-05-01",
                defaultValue = "2026-05-01"
        )
        LocalDate transactionDate
) {
}
