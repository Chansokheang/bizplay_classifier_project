package com.api.bizplay_classifier_api.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RuleRequest {
    @NotNull(message = "Company Id can not be null.")
    private String companyId;

    @NotEmpty(message = "Category code list can not be empty.")
    @JsonAlias("categoryIds")
    private List<String> categoryCodes;

    @NotBlank(message = "가맹점업종명 can not be blank.")
    @JsonProperty("\uAC00\uB9F9\uC810\uC5C5\uC885\uBA85")
    @JsonAlias({"merchantIndustryName", "businessType", "business_type"})
    private String merchantIndustryName;

    @NotBlank(message = "가맹점업종코드 can not be blank.")
    @Pattern(regexp = "^[A-Za-z0-9]{5}$", message = "가맹점업종코드 must be exactly 5 alphanumeric characters.")
    @JsonProperty("\uAC00\uB9F9\uC810\uC5C5\uC885\ucf54\ub4dc")
    @JsonAlias("merchantIndustryCode")
    private String merchantIndustryCode;

    private Integer minAmount;
    private Integer maxAmount;
    private String description;
}
