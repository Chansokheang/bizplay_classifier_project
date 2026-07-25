package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One turn of the BizPlay form-driven plan agent. {@code draftJson} is the session's bizplay zone:
 * the chosen purpose, the retrieved field spec, and the save-ready draft {@code document} (③ shape).
 * {@code pendingChoices} reuses the trip-plan chip contract (kind PURPOSE while the trip type is
 * unresolved). {@code missingFields} lists the labels of required-but-empty form fields.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BizplayPlanAgentResponse {
    private String sessionId;
    private String status;
    private String intent;
    private List<String> subAgents;
    private String reply;
    private List<TripPlanAgentResponse.PendingChoice> pendingChoices;
    private List<String> missingFields;
    /** Traveler NAMES held by the agent until they can be resolved to corporationUserIds. */
    private List<String> travelers;
    /** Resolved traveler corporationUserIds (same order they were resolved in). */
    private List<Long> travelerIds;
    /** Destination held by the agent (in the save body it rides the BSTR_PERIOD selections). */
    private String destination;
    /** EXACTLY the plan-draft request-body array — the retrieved form's structure, values only. */
    private JsonNode draftJson;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
