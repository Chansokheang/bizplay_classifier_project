package com.api.bizplay_conversational.service.bizplayGatewayService;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Thin gateway over the BizPlay cloud API (the company's system of record for trip plans).
 * All calls authenticate with the END USER's Bearer token (passed through from the UI).
 *
 * Endpoints wrapped:
 *  ① GET /api/v2/bstrPurpose/corporation-user/{corpUserId}/{paperKindType}  — purpose catalog
 *  ② GET /api/v2/paper/purpose/{purposeId}?segmentId={segmentId}           — form (paper) definitions
 */
public interface BizplayGatewayService {

    /** ① Purpose catalog (purposes + segments) for a corporation user. Cached briefly. */
    JsonNode getPurposeCatalog(String corpUserId, String token);

    /**
     * ② All paper definitions for a purpose/segment. Cached briefly. The caller filters by
     * paperKind.paperKindType (e.g. BSTR_PLAN for plan creation).
     */
    JsonNode getPapers(long purposeId, Long segmentId, String token);

    /** All staff of a corporation ({users:[...], count}). Cached briefly. */
    JsonNode getCorporationUsers(long corporationId, String token);

    /** One staff member's detail by corporationUserId. */
    JsonNode getUser(long corporationUserId, String token);

    /**
     * ③ Save the plan draft: POST the draft_json array (one document per traveler) to
     * /api/v2/approval/bics/bstr/plan/draft. Returns the server's raw response text
     * (e.g. "작성되었습니다."). Never cached.
     */
    String postPlanDraft(JsonNode documents, String token);
}
