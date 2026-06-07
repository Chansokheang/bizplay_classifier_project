package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Response of one Expense Report Agent turn (twin of {@link TripPlanAgentResponse}). */
@Getter
@Builder
public class ExpenseReportAgentResponse {
    private String sessionId;
    private String status;
    private String intent;
    private boolean delegated;
    /** All sub-agents that ran for this turn (may be several when receipt pipelines fan out). */
    private List<String> subAgents;
    private String reply;
    private JsonNode draftJson;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
