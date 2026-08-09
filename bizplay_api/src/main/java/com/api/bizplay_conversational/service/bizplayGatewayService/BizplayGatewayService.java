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
     * /api/v2/approval/{productCode}/bstr/plan/draft. Returns the server's raw response text
     * (e.g. "작성되었습니다."). Never cached.
     */
    String postPlanDraft(JsonNode documents, String token);

    /**
     * ⑦ Save the settlement (출장정산) draft — a SEPARATE endpoint and body from the plan draft.
     * POSTs the settlement draft_json array (the 정산서 request-body shape, one document per
     * settlement) to /api/v2/approval/{productCode}/bstr/report/draft. Returns the server's raw
     * response text. Never cached. The plan and settlement bodies never share this call: plan goes
     * to /bstr/plan/draft via {@link #postPlanDraft}, settlement to /bstr/report/draft here.
     */
    String postSettlementDraft(JsonNode documents, String token);

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
                                   java.util.List<String> cardTypes,
                                   java.util.List<Long> tranKindIds,
                                   java.util.List<Long> excludeTranKindIds, String token);

    /**
     * ⑧ Manual expense entry (기타카드 영수증 일괄 등록): POST an array of EtcReceiptSaveRequest
     * objects to /api/v2/receipt/etc-card. Returns the created receipt ids (the provider responds
     * with an array of int64). Used when a settlement has no matching card receipt to attach.
     */
    java.util.List<Long> postEtcCardReceipts(JsonNode expenses, String token);

    /**
     * ⑧ Upload a receipt image to the filebox (multipart field {@code multipartFile}):
     * POST /api/v2/filebox/upload. Returns the first uploaded file's id (UploadFileboxResponse.fileId).
     */
    long uploadReceiptFile(byte[] content, String filename, String token);

    /**
     * ⑧ Issued-receipt detail for the given receipt ids: GET /api/v2/receipt/issued/bulk/{ids}
     * (ids comma-joined). Returns the provider's IssuedReceiptDto array.
     */
    JsonNode getIssuedReceiptsBulk(java.util.List<Long> receiptIds, String token);

    /**
     * TranKind master (기타카드 TranKind list): GET /api/v2/trankind/list — every TranKind
     * registered for the company ({@code id, name, type, activated, scope, manageByAdmin}). Cached;
     * used to resolve a plan's allowed tranKind ids to their name/type.
     */
    JsonNode getTranKindList(String token);

    /**
     * ⑧ Update a created etc receipt's ADDITIONAL detail (기타증빙 단건 수정): PATCH
     * /api/v2/receipt-etc/{receiptId} with a ReceiptEtcDto body (etcReceiptType, usedStartDate/End,
     * vehicleType, depart/arrival, …). Optional — only called when the user supplies extra info.
     * Returns the provider's raw response text.
     */
    String patchEtcReceiptDetail(long receiptId, JsonNode detail, String token);
}
