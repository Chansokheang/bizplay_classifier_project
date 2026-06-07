package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A persisted row of conversational_trip_report: one expense line of a report, linking the trip plan
 * to either a cost or transportation expense. Exactly one of costExpenseId / transportationExpenseId
 * is set, matching section_code (enforced by a DB CHECK constraint).
 */
@Getter
@Setter
@NoArgsConstructor
public class TripReport {
    private UUID id;
    private UUID agentSessionId;
    private UUID departmentId;
    private UUID tripPlanId;
    private UUID transportationExpenseId;
    private UUID costExpenseId;
    /** "COST", "TRANSPORTATION", or "ETC". */
    private String sectionCode;
    private String excessReason;
    private String briefs;
    private String note;
    private String approvalNumber;
}
