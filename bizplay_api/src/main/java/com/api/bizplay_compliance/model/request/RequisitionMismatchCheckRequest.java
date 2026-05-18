package com.api.bizplay_compliance.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record RequisitionMismatchCheckRequest(
        @Schema(
                description = "Dummy requisition id for R10 testing.",
                example = "REQ-2026-0512-001",
                defaultValue = "REQ-2026-0512-001"
        )
        String requisitionId,
        @Schema(
                description = "Dummy approved transaction reference tied to the requisition.",
                example = "03712301",
                defaultValue = "03712301"
        )
        String transactionReference
) {
}
