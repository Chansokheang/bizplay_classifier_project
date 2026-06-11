package com.api.bizplay_compliance.model.request;

import java.util.List;

/** A batch of compliance-audit ids to delete. */
public record ComplianceAuditBatchDeleteRequest(List<String> ids) {
}
