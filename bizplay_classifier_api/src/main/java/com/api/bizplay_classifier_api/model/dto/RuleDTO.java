package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RuleDTO {
    private UUID ruleId;
    private UUID companyId;
    private String ruleName;
    private String merchantName;
    private String merchantIndustryName;
    private String usageStatus;
    private Integer minAmount;
    private Integer maxAmount;
    private String description;
    private Timestamp createdDate;
    private List<CategoryDTO> categoryDTOList;
}
