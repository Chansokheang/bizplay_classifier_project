package com.api.bizplay_chatbot.corp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CorpGroupCreateRequest {

    @Schema(description = "Natural business code for the group (UNIQUE).", example = "ACME-GRP")
    @NotBlank
    @Size(max = 20)
    private String corpGroupCd;
}
