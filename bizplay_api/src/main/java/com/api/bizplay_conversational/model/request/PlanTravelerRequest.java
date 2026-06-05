package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanTravelerRequest {

    @JsonProperty("Name")
    @Schema(example = "John Doe")
    @Size(max = 100)
    private String name;

    @JsonProperty("Department")
    @Schema(example = "Sales")
    @Size(max = 100)
    private String department;

    @JsonProperty("Position")
    @Schema(example = "Manager")
    @Size(max = 100)
    private String position;

    @JsonProperty("Origin")
    @Schema(example = "Seoul")
    @Size(max = 255)
    private String origin;

    @JsonProperty("Destination")
    @Schema(example = "Busan")
    @Size(max = 255)
    private String destination;

    @JsonProperty("ReturnPoint")
    @Schema(example = "Seoul")
    @Size(max = 255)
    private String returnPoint;
}
