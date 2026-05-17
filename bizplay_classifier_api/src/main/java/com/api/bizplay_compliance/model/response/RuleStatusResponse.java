package com.api.bizplay_compliance.model.response;

public record RuleStatusResponse(
        String ruleId,
        String ruleName,
        String status,
        String info
) {
}
