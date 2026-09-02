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
    /** Country of the resolved destination city (display only — the save body has no country field). */
    private String destinationCountry;
    /** 출장지 상세 (saved as the period row's selectionMemo). "" = asked and skipped; null = not asked yet. */
    private String destinationDetail;
    /** Per-day destinations when days differ: [{date, place, country, detail}], date-sorted. */
    private JsonNode periodPlaces;
    /** Departure place (출발지) held by the agent — no slot in the save body; place-validated. */
    private String origin;
    /** EXACTLY the plan-draft request-body array — the retrieved form's structure, values only. */
    /**
     * What this turn CHANGED, so a client knows which parts of its preview to redraw instead of
     * guessing from the reply text: "travellers", "route", "destination", "period", or "all".
     * Deterministic — the server just made the change, so it says so.
     */
    private List<String> uiRefresh;

    /**
     * True when the route legs carry the PAPER'S DEFAULT vehicle (flight for an overseas trip)
     * because the user never named one - so a preview can show it as a default rather than as
     * something they chose. The legs always need a transportType; this says where it came from.
     */
    private Boolean transportDefaulted;

    private JsonNode draftJson;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
