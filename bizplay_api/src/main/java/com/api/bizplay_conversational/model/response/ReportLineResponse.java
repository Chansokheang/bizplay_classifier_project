package com.api.bizplay_conversational.model.response;

import com.api.bizplay_conversational.model.entity.CostExpense;
import com.api.bizplay_conversational.model.entity.TransportationExpense;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * A single expense-report line (one conversational_trip_report row) with its linked expense.
 * Exactly one of costExpense / transportationExpense is populated, matching sectionCode.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportLineResponse {
    private UUID id;
    private UUID tripPlanId;
    private UUID agentSessionId;
    /** "COST", "TRANSPORTATION", or "ETC". */
    private String sectionCode;
    private UUID departmentId;
    private String department;
    private String excessReason;
    private String briefs;
    private String note;
    private String approvalNumber;
    /** Set for COST / ETC lines. */
    private CostExpense costExpense;
    /** Set for TRANSPORTATION lines. */
    private TransportationExpense transportationExpense;
}
