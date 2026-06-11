package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripPlanAgentResponse {
    private String sessionId;
    private String status;
    private String intent;
    private boolean delegated;
    /** All sub-agents that ran for this turn (may be several when they fan out in parallel). */
    private List<String> subAgents;
    private String reply;
    private JsonNode draftJson;
    /**
     * Structured disambiguation choices for this turn — present (non-null) only when an ambiguous
     * name (e.g. "sokheang") matched multiple staff and the user must pick one. The plain-text
     * {@code reply} carries the same information for old clients; the UI renders these as chips.
     */
    private List<PendingChoice> pendingChoices;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    /** One ambiguous mention the user must resolve. */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PendingChoice {
        /** What kind of entity is ambiguous. Currently only "STAFF". */
        private String kind;
        /** The ambiguous input as the user typed/extracted it (e.g. "sokheang"). */
        private String name;
        /** The candidate options to choose from. */
        private List<Option> options;
    }

    /** One selectable candidate for a {@link PendingChoice}. */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Option {
        private UUID staffId;
        /** Human-readable label, e.g. "Chan Sokheang (IT / Developer)". */
        private String label;
        /**
         * The exact text the UI should send as the next chat message to pick this option, e.g.
         * "Chan Sokheang from IT". Must be a phrase the disambiguation resolver already understands.
         */
        private String sendText;
    }
}
