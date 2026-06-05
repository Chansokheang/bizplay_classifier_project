package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TripInformationRequest {

    @JsonProperty("Purpose")
    @Schema(example = "General Business Trip")
    @NotBlank
    @Size(max = 255)
    private String purpose;

    @JsonProperty("BusinessPeriod")
    @Schema(example = "2024-07-01 to 2024-07-05")
    @NotBlank
    @Size(max = 100)
    private String businessPeriod;

    @JsonProperty("Destination")
    @Schema(example = "Busan")
    @NotBlank
    @Size(max = 255)
    private String destination;

    @JsonProperty("Title")
    @Schema(example = "Business Trip to Busan")
    @NotBlank
    @Size(max = 255)
    private String title;

    @JsonProperty("Content")
    @Schema(example = "Meeting with clients and visiting local offices.")
    private String content;

    @JsonProperty("Business Trip Classifcation")
    @JsonAlias({"Business Trip Classification", "BusinessTripClassification"})
    @Schema(example = "CS")
    @Size(max = 100)
    private String businessTripClassification;

    @JsonProperty("Travelers")
    @NotEmpty
    @Valid
    private List<PlanTravelerRequest> travelers = new ArrayList<>();
}
