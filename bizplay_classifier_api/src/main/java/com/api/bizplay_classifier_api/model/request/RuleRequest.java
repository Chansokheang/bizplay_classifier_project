package com.api.bizplay_classifier_api.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RuleRequest {
    @NotNull(message = "Company Id can not be null.")
    private UUID companyId;

    @NotEmpty(message = "Category Id list can not be empty.")
    private List<UUID> categoryIds;

    @NotBlank(message = "Rule name can not be blank.")
    private String ruleName;
    @NotBlank(message = "Merchant name can not be blank.")
    @JsonProperty("\uAC00\uB9F9\uC810\uBA85")
    @JsonAlias("merchantName")
    private String merchantName;

    @JsonProperty("\uAC00\uB9F9\uC810\uC5C5\uC885\uBA85")
    @JsonAlias({"merchantIndustryName", "businessType", "business_type"})
    private String merchantIndustryName;
    private Integer minAmount;
    private Integer maxAmount;
    private String description;
}
