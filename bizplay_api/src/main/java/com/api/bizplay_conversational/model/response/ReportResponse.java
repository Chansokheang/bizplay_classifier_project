package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A full business trip report: the header (conversational_trip_report) plus all of its expense lines
 * (conversational_trip_report_detail), each with its cost/transportation expense.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportResponse {
    private UUID id;
    private UUID tripPlanId;
    private UUID agentSessionId;
    private UUID departmentId;
    private String department;
    private String approvalStatus;
    private String approvalNumber;
    private LocalDateTime createdAt;
    /** All expense lines of this report. */
    private List<ReportDetailResponse> costItems;
}
