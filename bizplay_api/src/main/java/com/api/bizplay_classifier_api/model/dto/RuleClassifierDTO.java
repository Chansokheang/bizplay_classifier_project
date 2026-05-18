package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RuleClassifierDTO {
    private String merchantIndustryName;
    private String merchantIndustryCode;
    private String description;
    private String code;
    private String category;
}
