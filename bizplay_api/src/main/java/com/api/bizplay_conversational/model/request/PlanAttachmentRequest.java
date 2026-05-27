package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlanAttachmentRequest {

    @JsonProperty("Type")
    @NotBlank
    @Size(max = 20)
    private String type;

    @JsonProperty("FileID")
    @Size(max = 100)
    private String fileId;

    @JsonProperty("URL")
    @Size(max = 2048)
    private String url;
}
