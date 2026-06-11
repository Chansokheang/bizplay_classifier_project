package com.api.bizplay_compliance.model.request;

/**
 * Input for the v2 R10 (requisition mismatch) audit: which approved trip plan, in which corp, to
 * audit its expense report against. Both fields are required; validated in the service.
 */
public record RequisitionAuditRequest(String corpNo, String tripPlanId) {
}
