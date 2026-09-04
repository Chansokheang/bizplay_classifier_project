package com.api.bizplay_conversational.model.request;

import lombok.Getter;
import lombok.Setter;

/**
 * One chat turn of the BizPlay form-driven plan agent. {@code corpNo} scopes the conversational
 * session (our tenant key, FK to corp); {@code corpUserId} is the BizPlay corporation-user whose
 * purpose catalog / forms are used. The user's BizPlay token travels in the X-Bizplay-Token header.
 */
@Getter
@Setter
public class BizplayPlanAgentRequest {
    private String corpNo;
    private String corpUserId;
    /** BizPlay corporationId for roster lookups (e.g. 1805). Falls back to the token's currentCorpId. */
    private Long corporationId;
    private String sessionId;
    private String message;
    /**
     * Resolved traveler corporationUserIds (e.g. from the UI's user picker). When present, the draft
     * fans out to ONE document per traveler — exactly like the BizPlay save body. Until then the
     * draft holds a single document and traveler NAMES wait in agent state.
     */
    private java.util.List<Long> travelerCorpUserIds;
    /**
     * Uploaded file ids (spreadsheets = who travels; PDFs = where/when/why). Extracted content is
     * fed through the same field-mapper / traveler-resolver pipeline as typed text.
     */
    private java.util.List<String> fileIds;
    /**
     * The approved 출장계획서 a booking belongs to — used by the booking agent only, and the single
     * field that binds a booking to a plan and, through it, to the settlement. Null everywhere else.
     */
    private Long bstrPlanApprovalId;

    /** Optional 출장지 상세 (Trip Destination Details) — saved as the period row's selectionMemo. */
    private String destinationDetail;

    /**
     * A title the USER typed into the form before/while chatting. It is a VALUE, not context:
     * without it the freshly loaded document has no title and the agent composes one, which is
     * how a typed title ended up replaced by "9월 8일부터 10일까지 오사카 해외출장".
     */
    private String title;
}
