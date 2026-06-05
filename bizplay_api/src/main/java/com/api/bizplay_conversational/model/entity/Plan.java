package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Plan {
    private UUID id;
    private String corpNo;
    private String userReqId;
    private UUID agentSessionId;
    private String planType;
    private String purpose;
    private String businessPeriod;
    private LocalDate businessStartDate;
    private LocalDate businessEndDate;
    private String destination;
    private String title;
    private String content;
    private String businessTripClassification;
    private JsonNode extras = JsonNodeFactory.instance.objectNode();
    private List<PlanAttachment> attachments = new ArrayList<>();
    private List<PlanTraveler> travelers = new ArrayList<>();
    private List<ExpenseSection> expenseSections = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
