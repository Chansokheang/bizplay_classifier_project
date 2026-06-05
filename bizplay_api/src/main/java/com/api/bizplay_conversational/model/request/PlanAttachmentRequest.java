package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlanAttachmentRequest {

    @JsonProperty("Type")
    @Schema(example = "File", description = "File or URL")
    @NotBlank
    @Size(max = 20)
    private String type;

    @JsonProperty("FileID")
    @Schema(example = "FILE_ID_001")
    @Size(max = 100)
    private String fileId;

    @JsonProperty("URL")
    @Schema(example = "https://example.com/itinerary.pdf")
    @Size(max = 2048)
    private String url;
}
