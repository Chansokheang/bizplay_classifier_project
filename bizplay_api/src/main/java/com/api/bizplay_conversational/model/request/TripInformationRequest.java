package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TripInformationRequest {

    @JsonProperty("Purpose")
    @NotBlank
    @Size(max = 255)
    private String purpose;

    @JsonProperty("BusinessPeriod")
    @NotBlank
    @Size(max = 100)
    private String businessPeriod;

    @JsonProperty("Destination")
    @NotBlank
    @Size(max = 255)
    private String destination;

    @JsonProperty("Title")
    @NotBlank
    @Size(max = 255)
    private String title;

    @JsonProperty("Content")
    private String content;

    @JsonProperty("Business Trip Classifcation")
    @JsonAlias({"Business Trip Classification", "BusinessTripClassification"})
    @Size(max = 100)
    private String businessTripClassification;

    @JsonProperty("Travelers")
    @NotEmpty
    @Valid
    private List<PlanTravelerRequest> travelers = new ArrayList<>();
}
