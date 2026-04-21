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
    @Schema(example = "1234567890")
    private String corpNo;

    @NotNull(message = "Corp group id can not be null.")
    @JsonAlias({"corpGroupId", "corp_group_id"})
    @Schema(example = "1")
    private Long corpGroupId;
}

