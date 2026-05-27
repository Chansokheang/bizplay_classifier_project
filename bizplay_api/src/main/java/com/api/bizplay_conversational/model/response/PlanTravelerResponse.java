package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PlanTravelerResponse {
    private UUID id;
    private String name;
    private String department;
    private String position;
    private String origin;
    private String destination;
    private String returnPoint;
}
