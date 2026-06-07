package com.api.bizplay_conversational.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {
    private UUID id;
    private String corpNo;
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
    private String approvalStatus;
    private List<PlanAttachmentResponse> attachments;
    private List<PlanTravelerResponse> travelers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
