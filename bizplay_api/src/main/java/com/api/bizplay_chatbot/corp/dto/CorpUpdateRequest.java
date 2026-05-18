package com.api.bizplay_chatbot.corp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PATCH semantics: omitted fields stay unchanged. {@code corpNo} is intentionally
 * absent — it is the path identifier and immutable. To change the identifier,
 * delete and re-create.
 */
@Data
public class CorpUpdateRequest {

    @Schema(description = "Replacement corp_group id. Pass null to leave unchanged.",
            example = "2")
    private Long corpGroupId;

    @Schema(description = "Replacement display name. Pass null to leave unchanged.",
            example = "ACME Holdings International")
    @Size(max = 255)
    private String corpName;
}
