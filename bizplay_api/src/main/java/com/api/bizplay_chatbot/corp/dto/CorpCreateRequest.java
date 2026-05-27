package com.api.bizplay_chatbot.corp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CorpCreateRequest {

    @Schema(description = "Natural business identifier — issued by the external login service. UNIQUE.",
            example = "ACME-001")
    @NotBlank
    @Size(max = 50)
    private String corpNo;

    @Schema(description = "ID of the owning corp_group. Hard FK — must already exist.",
            example = "1")
    @NotNull
    private Long corpGroupId;

    @Schema(description = "Display name of the corporation.", example = "ACME Holdings")
    @NotBlank
    @Size(max = 255)
    private String corpName;
}
