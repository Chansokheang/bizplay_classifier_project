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
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PendingChoice {
        /** What kind of entity is ambiguous. Currently only "STAFF". */
        private String kind;
        /**
         * The entry in the response's `resources` that serves the FULL list behind these options —
         * set when the chips are a shortlist. The controller resolves it to {@link #optionsUrl}.
         */
        private String source;
        /**
         * The BIZPLAY endpoint that serves this same list, for a client holding its own bearer.
         * A path, not a URL — prefix your own host. Several paths separated by " · " when the
         * list is assembled from more than one call.
         */
        private java.util.List<java.util.Map<String, String>> upstream;
        /**
         * How this list is meant to be drawn: "chips" (a short row of buttons), "dropdown" (too
         * long for chips), "table" (the options carry columns in meta), "route-picker" (departure /
         * destination / return per traveller) or "approval-line" (people plus their role). A hint,
         * not a rule — the options answer the question whatever the client draws.
         */
        private String render;
        /**
         * Where this same list is served, when an endpoint serves it — corpNo already filled in.
         * The options above are complete enough to answer the question; this is for a client that
         * wants to search, page or refresh the list on its own. Null when no endpoint backs it.
         */
        private String optionsUrl;
        /**
         * For a long list: the BizPlay endpoint the client can call DIRECTLY for the full list -
         * {@code {method, path, labelField, filter?, then?}}. Show {@code labelField} of each row
         * and send the chosen row's label back as the next message; the agent resolves it. This is
         * the primary way to fetch a lookup: a client that already reaches BizPlay's API needs no
         * proxy to the AI server. {@code optionsUrl} is only our convenience mirror of the same list.
         */
        private java.util.Map<String, Object> lookup;
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
        /**
         * Optional structured columns behind the label — used when the option is richer than a
         * chip, e.g. the settlement flow's plan picker, which the UI renders as a table
         * (purpose / title / docNo / startDate / endDate / drafter / registrar).
         */
        private java.util.Map<String, String> meta;
    }
}
