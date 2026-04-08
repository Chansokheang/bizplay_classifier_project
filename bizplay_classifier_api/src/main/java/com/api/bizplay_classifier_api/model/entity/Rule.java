package com.api.bizplay_classifier_api.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Rule {
    private UUID ruleId;
    private UUID companyId;
    private String merchantIndustryName;
    private String merchantIndustryCode;
    private String usageStatus;
    private Integer minAmount;
    private Integer maxAmount;
    private String description;
    private Timestamp createdDate;
}
