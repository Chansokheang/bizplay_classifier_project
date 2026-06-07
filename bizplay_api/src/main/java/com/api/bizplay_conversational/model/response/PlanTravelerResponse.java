package com.api.bizplay_conversational.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanTravelerResponse {
    private UUID id;
    private String name;
    private String department;
    private String position;
    private String origin;
    private String destination;
    private String returnPoint;
}
