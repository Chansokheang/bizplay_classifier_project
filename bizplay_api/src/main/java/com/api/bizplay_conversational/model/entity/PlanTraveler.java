package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PlanTraveler {
    private UUID id;
    private Plan plan;
    private Staff staff;
    private String origin;
    private String destination;
    private String returnPoint;
    private LocalDateTime createdAt;
}
