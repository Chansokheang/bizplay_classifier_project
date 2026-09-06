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
@Builder(toBuilder = true)
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

    /**
     * The endpoints a client of THIS agent may call, corpNo already filled in — so "which API do I
     * call for this?" is answered by the response instead of by documentation. Nothing here is
     * required to hold a conversation: every question the agent asks carries its own
     * {@code pendingChoices}. These are for the widgets a client builds on its own — the
     * manual-expense form's 통화/세금코드/터미널 lists, the approver directory, a destination search.
     */
    /**
     * The widget this turn is asking for, when it asks for more than a sentence: "expense-form"
     * (draw {@link #formFields}), "file-upload" (a receipt image), "calendar" (a date range),
     * "expense-preview" / "settlement-preview" (show the draft and confirm), "receipt-table",
     * "confirm-submit". Null when the reply plus {@link #pendingChoices} is the whole turn.
     */
    private String ui;

    /**
     * The endpoint the widget above submits to, corpNo filled in — e.g. the multipart upload for
     * "file-upload", the create call for "confirm-submit". Null when the answer is just a message.
     */
    private String action;

    /**
     * The inputs of the form this turn is asking for, when it asks for one (the manual-expense
     * receipt): [{key, label, type, required, options|source}]. `source` names the entry in
     * {@link #resources} that serves that list; the controller resolves it to `optionsUrl`.
     */
    private java.util.List<java.util.Map<String, Object>> formFields;

    private java.util.Map<String, java.util.List<java.util.Map<String, String>>> resources;

    /**
     * The BizPlay endpoints behind {@link #resources}, keyed identically — for a client that calls
     * the provider directly with its own bearer. Paths only; the caller prefixes its own host.
     */
    private java.util.Map<String, java.util.List<java.util.Map<String, String>>> upstream;

    /**
     * The approval line the agent is holding for this plan: [{corporationUserId, approvalKindType,
     * name}], in order, drafter excluded. Set as the traveller names approvers in conversation, and
     * used at create time — so a client can render the line instead of reading it out of the reply.
     */
    private JsonNode approvalLines;

    private JsonNode draftJson;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
