package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PlanCreateRequest {

    @JsonProperty("CorpNo")
    @NotBlank
    @Size(max = 50)
    private String corpNo;

    @JsonProperty("PlanType")
    @NotBlank
    @Size(max = 100)
    private String planType;

    @JsonProperty("Attachemnt")
    @JsonAlias({"Attachment", "Attachments"})
    @Valid
    private List<PlanAttachmentRequest> attachments = new ArrayList<>();

    @JsonProperty("TripInformation")
    @NotNull
    @Valid
    private TripInformationRequest tripInformation;
}
