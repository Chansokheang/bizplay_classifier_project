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

    /**
     * ④b The traveler's plans from the UNSCOPED path
     * (GET /api/v2/approval/bstr/plan/list) — the only one that returns DRAFTED rows, i.e. the
     * requests still waiting for an approver. {@link #getPlanList} is product-scoped and answers
     * with APPROVED plans only, so it can never show what is still pending.
     * <p>Caveats of this path, left to the caller: the period query is IGNORED (the provider
     * returns the whole history), usageCnt is null, and each plan repeats once per approval line.
     */
    JsonNode getPendingPlanList(long travelerId, String startDate, String endDate, String token);

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
     * Personal-card general-expense browser: POST /api/v3/receipt/cloud/personal-card/my/general-expense
     * with {startDate, endDate, approvalStatusTypeList, pageIndex, pageSize}. Default status NOT_DRAFTED
     * (issued, not yet in a document); NOT_ISSUED for incomplete receipts. Returns the receipt-row array.
     */
    JsonNode getGeneralExpenses(String startDate, String endDate, java.util.List<String> statusList,
                                int pageIndex, int pageSize, String token);

    /** One receipt's full detail (used for NOT_ISSUED rows): GET /api/v2/receipt/{receiptId}. */
    JsonNode getReceiptById(long receiptId, String token);

    /** Attach uploaded files to a receipt: PATCH /api/v2/receipt/image/{receiptId} body [fileId, …]. */
    String attachReceiptImages(long receiptId, java.util.List<Long> fileIds, String token);

    /**
     * TranKind master (기타카드 TranKind list): GET /api/v2/trankind/list — every TranKind
     * registered for the company ({@code id, name, type, activated, scope, manageByAdmin}). Cached;
     * used to resolve a plan's allowed tranKind ids to their name/type.
     */
    JsonNode getTranKindList(String token);

    /**
     * Who the given bearer token belongs to (name, corporationUserId, department, corporations).
     * NEVER cached: the answer is per-token, and a cache would hand one user another's identity.
     */
    JsonNode getUserProfile(String token);

    /** Budget departments (코스트센터) usable in a slip: the user's authorized list when one is
     *  registered, else the corp-wide list. Never throws — an empty array on failure. */
    JsonNode getBudgetDepartments(long corpUserId, String token);

    /** ⑨ Settlement (출장정산서) documents in a period — POST filter, streaming JSON response. */
    JsonNode getSettlementList(String startDate, String endDate, String token);

    /**
     * Transport terminals/stations (교통수단 별 역이름 목록): GET /api/v2/receipt/etc-card/terminal —
     * TerminalDto[] ({@code id, vehicleType, name, mode}) for the depart/arrival dropdowns. Cached.
     * AIR entries are airports, KTX entries are all rail stations, BUS entries are bus terminals.
     */
    JsonNode getEtcCardTerminals(String token);

    /**
     * Public-transport nodes for one vehicle type (CBUS, KTX, BUS, AIR): an array of
     * {@code {nodeId, nodeName, vehicleType}}. Cached - it is static reference data and CBUS alone
     * returns over two thousand stops.
     */
    JsonNode getVehicleNodes(String vehicleType, String token);

    /**
     * ⑧ Update a created etc receipt's ADDITIONAL detail (기타증빙 단건 수정): PATCH
     * /api/v2/receipt-etc/{receiptId} with a ReceiptEtcDto body (etcReceiptType, usedStartDate/End,
     * vehicleType, depart/arrival, …). Optional — only called when the user supplies extra info.
     * Returns the provider's raw response text.
     */
    String patchEtcReceiptDetail(long receiptId, JsonNode detail, String token);

    // --- Plan region / route enrichment (docs/bstr-plan-save-api-guide.md) -------------------

    /** Region master list: regionType = SIDO | COUNTRY → BstrRegionDto[]. Cached (reference data). */
    JsonNode getRegionList(String regionType, String token);

    /** Cities of one country (full list) → BstrRegionDto[]. Cached. */
    JsonNode getRegionCities(String countryCode, String token);

    /** 급지-registered regions only: regionType = SIDO | COUNTRY. Cached. */
    JsonNode getUsedRegionList(String regionType, String token);

    /** 급지-registered cities of one country. Empty means "all cities" OR "not registered" — the
     *  caller disambiguates via {@link #getUsedRegionList}. Cached. */
    JsonNode getUsedRegionCities(String countryCode, String token);

    /** One region by id — how a saved selectionId is turned back into city + country. */
    JsonNode getRegionById(long regionId, String token);

    /** Company-registered trip destinations (id, coordinates, address). Cached. */
    JsonNode getPlanDestinations(String token);

    /** Server-side bypass to TMap: POST the bypass envelope, returns TMap's JSON verbatim. */
    JsonNode postBypass(JsonNode envelope, String token);

    /**
     * 규정조회 (company feedback #9). POST the trip + expense facts to /bstr/policy/limit and get
     * the 용도's own policy back: 지급구분 (ACTUAL 실비 / LIMITED 한도 / FIXED 정액 / ACTUAL_FIXED),
     * the 한도 금액 and its currency, per-day limits, and whether an 초과사유 is required. Returns
     * null when the corp has no policy for that 용도 (the provider answers 200 with an EMPTY body -
     * a real answer, not an error).
     */
    JsonNode getPolicyLimit(JsonNode request, String token);

    /**
     * 세금코드 master: id, taxCode ("V0"), taxName, taxRate, deductionStatus and the account subject
     * each code is assigned to. Cached - it is corp master data.
     */
    JsonNode getTaxCodes(String token);

    /**
     * The 용도 that carry a 출장비 규정, for one TranKind type ("TRANSPORT", "ROOM", "FOOD"...).
     * Each row also carries the 용도's debit account, which is what a settlement line's
     * accountSubject fields are filled from. Cached.
     */
    JsonNode getPolicyTranKinds(String tranKindType, String token);

    /**
     * Edit a registered 기타증빙: PATCH /receipt/etc-card/{receiptId} with a whole
     * EtcReceiptSaveRequest (base + detail). Used when the traveller corrects a value in the
     * settlement preview — the receipt itself has to change, not just our draft.
     */
    String patchEtcCardReceipt(long receiptId, JsonNode body, String token);

    /** 통화코드 목록: [{nation, currencyCodeName, name}] — 179 rows on cloud-dev. Cached. */
    JsonNode getCurrencyCodes(String token);

    /**
     * The day's 환율 for one currency, as {@code {fromCurrencyCode, currencyCode, exchangeRate,
     * standardDate, noticeTimes}}. Returns null when that day has no rate published — today's is
     * typically not there yet, so the caller walks back to the last day that has one.
     */
    JsonNode getExchangeRate(String fromCurrencyCode, String standardDate, String token);

    /**
     * How many units the rate is quoted FOR: 1 for USD, 100 for JPY ("일본 JPY (100)"). Read from
     * the rate detail's currencyName; 1 when the detail is unavailable.
     */
    int getCurrencyUnit(String fromCurrencyCode, String standardDate, String token);
}
