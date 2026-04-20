package com.api.bizplay_classifier_api.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditRuleDTO {
    private UUID id;
    private String ruleCode;
    private String name;
    private Integer scoreDelta;
    private Boolean isActive;
    private JsonNode params;
}
