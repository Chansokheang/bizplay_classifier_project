package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditRuleResultDTO {
    private UUID ruleId;
    private String ruleName;
    private Integer scoreDelta;
    private String detail;
}
