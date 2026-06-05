package com.api.bizplay_conversational.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TextAnalysisAgentRequest {

    @Schema(example = "John Doe travels from Seoul to Busan by KTX, returning to Seoul, from 2024-07-01 to 2024-07-05.")
    @NotBlank
    private String message;
}
