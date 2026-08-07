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
     *
     * Resolves in two steps: the untyped path discovers the purpose's papers (each carrying its
     * bstrType), then the TYPED path {@code /paper/purpose/{bstrType}/{purposeId}} — the variant
     * the real UI calls — returns that trip area's papers with area-dependent configuration
     * filled in. Falls back to the untyped definition if the typed call is unavailable.
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

    /**
     * ④ Search the traveler's business-trip plans (settlement anchor candidates):
     * GET /api/v2/approval/bstr/plan/list?travelerId=&searchPeriodType=BSTR_START_DATE
     * &startDate=&endDate=. Returns the provider's plain array of plan summaries.
     */
    JsonNode getPlanList(long travelerId, String startDate, String endDate, String token);

    /** ⑤ One plan's full detail by approvalId: GET /api/v2/approval/bstr/{approvalId}. */
    JsonNode getPlanDetail(long approvalId, String token);

    /**
     * ⑥ Unattached card receipts (settlement evidence candidates):
     * GET /api/v2/receipt/{productCode}/not-attached/stream. The endpoint returns a JSON
     * STREAM (concatenated root-level objects, no array wrapper) — parsed here into an array.
     * cardTypeList entries: CORP | PERSONAL | MY_DATA.
     */
    JsonNode getUnattachedReceipts(long corpUserId, String startDate, String endDate,
                                   java.util.List<String> cardTypes, String token);
}
