package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Report HEADER row (conversational_trip_report): one per report. The expense lines live in
 * conversational_trip_report_detail ({@link TripReportDetail}).
 */
@Getter
@Setter
@NoArgsConstructor
public class TripReport {
    private UUID id;
    private UUID agentSessionId;
    private UUID departmentId;
    private UUID tripPlanId;
    private String approvalNumber;
    private String approvalStatus;
    private java.time.LocalDateTime createdDate;
}
