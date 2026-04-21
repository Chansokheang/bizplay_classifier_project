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
    @NotNull(message = "Corp no can not be null.")
    @JsonAlias({"companyId"})
    private String corpNo;

    @NotEmpty(message = "Category code list can not be empty.")
    @JsonAlias("categoryIds")
    private List<String> categoryCodes;

    @NotBlank(message = "Merchant industry name can not be blank.")
    @JsonProperty("\uAC00\uB9F9\uC810\uC5C5\uC885\uBA85")
    @JsonAlias({"merchantIndustryName", "businessType", "business_type"})
    private String merchantIndustryName;

    @NotBlank(message = "Merchant industry code can not be blank.")
    @Pattern(regexp = "^[A-Za-z0-9]{5}$", message = "Merchant industry code must be exactly 5 alphanumeric characters.")
    @JsonProperty("\uAC00\uB9F9\uC810\uC5C5\uC885\uCF54\uB4DC")
    @JsonAlias("merchantIndustryCode")
    private String merchantIndustryCode;

    private Integer minAmount;
    private Integer maxAmount;
    private String description;

    public String getCompanyId() {
        return corpNo;
    }

    public void setCompanyId(String companyId) {
        this.corpNo = companyId;
    }
}
