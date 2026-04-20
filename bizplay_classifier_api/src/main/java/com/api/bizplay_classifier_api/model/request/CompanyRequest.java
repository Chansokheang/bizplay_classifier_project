package com.api.bizplay_classifier_api.model.request;

import com.api.bizplay_classifier_api.model.enums.CompanyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
@Builder
public class CompanyRequest {
    @NotBlank(message = "Company name can not be blank.")
    @NotNull
    @Schema(example = "BizPlay")
    private String companyName;

    @Schema(example = "1234567890")
    private String businessNumber;

    @Schema(
            example = "CLASSIFIER",
            defaultValue = "CLASSIFIER",
            allowableValues = {"CLASSIFIER", "AICOMPLIANCE", "CONVERSATIONAL"}
    )
    private CompanyType types;
}
