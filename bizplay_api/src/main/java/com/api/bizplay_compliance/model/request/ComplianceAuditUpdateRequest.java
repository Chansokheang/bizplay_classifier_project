package com.api.bizplay_compliance.model.request;

/**
 * Analyst override of a stored compliance audit's verdict. Both fields are optional — a null field
 * is left unchanged. {@code complianceStatus} must be NORMAL|SUSPICION, {@code confidenceLevel} must
 * be HIGH|MEDIUM|LOW (validated in the service).
 */
public record ComplianceAuditUpdateRequest(String complianceStatus, String confidenceLevel) {
}
