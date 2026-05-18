package com.api.bizplay_classifier_api.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class CorpRequest {
    @NotBlank(message = "Corp name can not be blank.")
    @NotNull
    @Schema(example = "BizPlay")
    private String corpName;

    @JsonAlias("corpNo")
    @NotBlank(message = "Corp number can not be blank.")
    @Schema(example = "1234567890")
    private String corpNo;

    @NotBlank(message = "Corp group code can not be blank.")
    @JsonAlias({"corpGroupCd", "corpGroupCode", "corp_group_cd"})
    @Schema(example = "GROUP001")
    private String corpGroupCode;
}
