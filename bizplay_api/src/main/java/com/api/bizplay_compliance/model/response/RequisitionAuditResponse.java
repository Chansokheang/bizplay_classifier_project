package com.api.bizplay_compliance.model.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Result of the v2 R10 (requisition mismatch) audit: how an approved trip plan (the requisition)
 * compares against its expense report + uploaded receipts, across a set of independently-reported
 * alignment dimensions.
 *
 * <p>{@code status} is the rule outcome — {@code Pass} (all evaluable dimensions aligned),
 * {@code Failed} (one or more mismatches), or {@code SKIPPED} (nothing could be evaluated).
 */
public record RequisitionAuditResponse(
        UUID auditId,
        String corpNo,
        UUID tripPlanId,
        String ruleId,            // always "R10"
        String ruleName,          // "Requisition Mismatch"
        String status,            // Pass | Failed | SKIPPED
        String complianceStatus,  // NORMAL | SUSPICION
        String confidenceLevel,   // HIGH | MEDIUM | LOW
        String summary,
        List<DimensionFinding> findings,
        LocalDateTime createdDate
) {

    /** One alignment dimension's verdict. {@code status} is PASS | FAIL | SKIPPED. */
    public record DimensionFinding(String dimension, String status, String detail) {
    }
}
