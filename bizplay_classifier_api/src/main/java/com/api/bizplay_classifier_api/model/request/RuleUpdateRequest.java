package com.api.bizplay_classifier_api.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
public class RuleUpdateRequest {
    @NotEmpty(message = "Category Id list can not be empty.")
    private List<UUID> categoryIds;

    @NotBlank(message = "가맹점업종명 can not be blank.")
    @JsonProperty("\uAC00\uB9F9\uC810\uC5C5\uC885\uBA85")
    @JsonAlias({"merchantIndustryName", "businessType", "business_type"})
    private String merchantIndustryName;

    @NotBlank(message = "가맹점업종코드 can not be blank.")
    @Pattern(regexp = "^[A-Za-z0-9]{5}$", message = "가맹점업종코드 must be exactly 5 alphanumeric characters.")
    @JsonProperty("\uAC00\uB9F9\uC810\uC5C5\uC885\ucf54\ub4dc")
    @JsonAlias("merchantIndustryCode")
    private String merchantIndustryCode;

    @Pattern(regexp = "^[YN]$", message = "Usage status must be Y or N.")
    private String usageStatus;

    private Integer minAmount;
    private Integer maxAmount;
    private String description;
}
