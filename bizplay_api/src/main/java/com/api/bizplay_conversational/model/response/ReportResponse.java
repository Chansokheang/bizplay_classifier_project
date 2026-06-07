package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** Result of creating a Business Trip Report: the persisted report lines and their linkage. */
@Getter
@Builder
public class ReportResponse {
    private UUID tripPlanId;
    private UUID agentSessionId;
    private String corpNo;
    private int costCount;
    private int transportationCount;
    private int etcCount;
    private int totalLines;
    private int attachmentCount;
    /** ids of the conversational_trip_report rows created (one per expense line). */
    private List<UUID> reportLineIds;
}
