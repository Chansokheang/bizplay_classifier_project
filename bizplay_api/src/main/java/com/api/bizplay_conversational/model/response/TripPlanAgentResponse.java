package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TripPlanAgentResponse {
    private String sessionId;
    private String status;
    private String intent;
    private boolean delegated;
    /** All sub-agents that ran for this turn (may be several when they fan out in parallel). */
    private List<String> subAgents;
    private String reply;
    private JsonNode draftJson;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
