package com.api.bizplay_chatbot.corp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PATCH semantics: omitted fields stay unchanged.
 */
@Data
public class CorpGroupUpdateRequest {

    @Schema(description = "Replacement natural business code. Pass null to leave unchanged.",
            example = "ACME-GRP-2")
    @Size(max = 20)
    private String corpGroupCd;
}
