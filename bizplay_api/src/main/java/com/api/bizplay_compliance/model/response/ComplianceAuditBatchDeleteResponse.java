package com.api.bizplay_compliance.model.response;

import java.util.List;

/** Result of a batch delete: how many ids were requested, how many rows were actually deleted. */
public record ComplianceAuditBatchDeleteResponse(int requested, int deleted, List<String> deletedIds) {
}
