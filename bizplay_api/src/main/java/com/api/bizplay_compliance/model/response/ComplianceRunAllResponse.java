package com.api.bizplay_compliance.model.response;

import java.util.List;

public record ComplianceRunAllResponse(
        String complianceStatus,
        String confidenceLevel,
        List<RuleStatusResponse> rules
) {
}
