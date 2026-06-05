package com.api.bizplay_conversational.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StaffLookupAgentRequest {

    @Schema(example = "1234567890")
    @NotBlank
    @Size(max = 50)
    private String corpNo;

    @Schema(example = "John Doe", description = "Optional. If omitted, the LLM extracts a name from message.")
    @Size(max = 100)
    private String name;

    @Schema(example = "John Doe will join the BUSAN trip.")
    @NotBlank
    private String message;
}
