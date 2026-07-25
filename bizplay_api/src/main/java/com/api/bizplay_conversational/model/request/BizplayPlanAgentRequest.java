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
}
