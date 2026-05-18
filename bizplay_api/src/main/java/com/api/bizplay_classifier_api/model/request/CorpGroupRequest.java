package com.api.bizplay_classifier_api.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CorpGroupRequest {
    @NotBlank(message = "Corp group code can not be blank.")
    @JsonAlias({"corpGroupCd", "corpGroupCode"})
    @Schema(example = "GROUP001")
    private String corpGroupCode;
}

