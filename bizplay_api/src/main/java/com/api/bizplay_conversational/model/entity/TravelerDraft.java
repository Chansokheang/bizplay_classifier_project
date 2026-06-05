package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TravelerDraft {

    @JsonIgnore
    private UUID staffId;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Department")
    private String department;

    @JsonProperty("Position")
    private String position;

    @JsonProperty("Origin")
    private String origin;

    @JsonProperty("Destination")
    private String destination;

    @JsonProperty("ReturnPoint")
    private String returnPoint;

    @JsonProperty("TransportationMethod")
    private String transportationMethod;
}
