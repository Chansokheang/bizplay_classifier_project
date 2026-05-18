package com.api.bizplay_compliance.model.response;

public record RuleCheckResponse(
        String ruleId,
        String ruleName,
        String status,
        String detail
) {
}
