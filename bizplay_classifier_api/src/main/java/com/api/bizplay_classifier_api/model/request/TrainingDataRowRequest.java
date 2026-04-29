package com.api.bizplay_classifier_api.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TrainingDataRowRequest {
    @NotBlank(message = "Merchant industry code can not be blank.")
    @JsonAlias({"merchant_industry_code", "usageIndustryCode", "가맹점업종코드"})
    private String merchantIndustryCode;

    @NotBlank(message = "Merchant industry name can not be blank.")
    @JsonAlias({"merchant_industry_name", "usageIndustryName", "가맹점업종명"})
    private String merchantIndustryName;

    @NotBlank(message = "Category code can not be blank.")
    @JsonAlias({"usageCode", "usage_code", "code", "용도코드"})
    private String categoryCode;

    @NotBlank(message = "Category name can not be blank.")
    @JsonAlias({"usageName", "usage_name", "category", "용도명"})
    private String categoryName;

    @JsonAlias("승인일자")
    private String approvalDate;

    @JsonAlias("승인시간")
    private String approvalTime;

    @JsonAlias("가맹점명")
    private String merchantName;

    @JsonAlias("가맹점사업자번호")
    private String merchantBusinessNumber;

    @JsonAlias("공급금액")
    private Integer supplyAmount;

    @JsonAlias("부가세액")
    private Integer vatAmount;

    @JsonAlias("과세유형")
    private String taxType;
}
