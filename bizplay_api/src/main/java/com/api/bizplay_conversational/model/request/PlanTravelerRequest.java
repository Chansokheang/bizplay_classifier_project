package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlanTravelerRequest {

    @JsonProperty("Name")
    @Size(max = 100)
    private String name;

    @JsonProperty("Department")
    @Size(max = 100)
    private String department;

    @JsonProperty("Position")
    @Size(max = 100)
    private String position;

    @JsonProperty("Origin")
    @Size(max = 255)
    private String origin;

    @JsonProperty("Destination")
    @Size(max = 255)
    private String destination;

    @JsonProperty("ReturnPoint")
    @Size(max = 255)
    private String returnPoint;
}
