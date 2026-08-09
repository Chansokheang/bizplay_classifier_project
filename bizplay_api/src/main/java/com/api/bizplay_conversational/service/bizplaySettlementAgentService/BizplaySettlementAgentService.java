package com.api.bizplay_conversational.service.bizplaySettlementAgentService;

import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Conversational settlement (출장정산) agent. Mirrors the plan agent's architecture —
 * rule-based orchestrator, chip-driven picks, session draft_json as the provider body —
 * but anchors every settlement to an EXISTING business-trip plan:
 *
 *   AWAIT_PERIOD          ask/parse the search window
 *   AWAIT_PLAN_PICK       ④ plan list -> user picks the trip to settle
 *   AWAIT_EVIDENCE_FILTER ⑤ plan detail imported -> ask evidence window + card types
 *   AWAIT_EVIDENCE_PICK   ⑥ unattached receipts -> user attaches evidence
 *   DONE                  receipts recorded in draft_json, ready to submit
 *   (create)              ⑦ POST the settlement draft_json to BizPlay's /bstr/report/draft
 */
public interface BizplaySettlementAgentService {

    BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken);

    /**
     * ⑦ Submit the session's settlement draft_json to BizPlay (its own /bstr/report/draft endpoint,
     * never the plan's). Optional {@code approvalLines} carries the "Set approval order" picks —
     * [{corporationUserId, approvalKindType?}] — appended after the drafter's DRAFT line.
     */
    BizplayPlanAgentResponse createSettlement(String sessionId, String corpNo, String bizplayToken,
                                              JsonNode approvalLines);

    /**
     * ⑧ Manual expense entry — used when the trip has no matching card receipt to attach. Registers
     * the gathered expense as a 기타카드 receipt (POST etc-card), uploads its required image
     * (filebox/upload), fetches the issued detail (issued/bulk), and maps it into the draft's
     * {@code etcReceiptSaveRequests[]} with exact keys, recomputing totals.
     *
     * @param expenseFields the EtcReceiptSaveRequest fields the user supplied (approvalDate/Time,
     *                      mestName, amounts, …); mapped key-for-key into the settlement body.
     * @param detail        OPTIONAL additional receipt detail (ReceiptEtcDto — vehicleType,
     *                      depart/arrival, used dates, …); when present, PATCHed to the created
     *                      receipt via /receipt-etc/{receiptId}. Null/empty = skip that step.
     * @param image         the receipt image bytes (required).
     */
    BizplayPlanAgentResponse addManualExpense(String sessionId, String corpNo, JsonNode expenseFields,
                                              JsonNode detail, byte[] image, String filename, String bizplayToken);

    /**
     * ⑧ STEP 1 — register the receipt with just the base fields (POST /receipt/etc-card). Returns the
     * created receipt id (stashed in the session) and the type-specific detail fields to collect next.
     */
    BizplayPlanAgentResponse createManualReceipt(String sessionId, String corpNo, JsonNode expenseFields,
                                                 String bizplayToken);

    /**
     * ⑧ STEP 2 — complete the receipt from STEP 1: PATCH /receipt-etc/{id} with the additional detail,
     * upload the optional image, and map the expense into the settlement draft.
     */
    BizplayPlanAgentResponse completeManualReceipt(String sessionId, String corpNo, JsonNode detail,
                                                   byte[] image, String filename, String bizplayToken);

    /**
     * Load a settlement session's current state (draft_json + status) without mutating it — used by the
     * UI to restore the registered-expenses table after the chat is closed and reopened (the receipts
     * are persisted in our DB as the session's draft_json for the time being).
     */
    BizplayPlanAgentResponse getSession(String corpNo, String sessionId);

    /**
     * Finalize the settlement in OUR DB only — marks the session APPROVED and persists its current
     * draft_json, independent of the BizPlay {@code report/draft} POST (which needs enrichment). Lets a
     * settlement be "saved" for the time being without leaving our database.
     */
    BizplayPlanAgentResponse saveSettlement(String corpNo, String sessionId);

    /**
     * List this corp's saved settlements (EXPENSE_REPORT sessions that have a draft) as lightweight
     * summary rows — {sessionId, status, title, total, receiptCount, created/updatedDate} — for a table
     * view. Full detail comes from {@link #getSession}.
     */
    java.util.List<java.util.Map<String, Object>> listSettlements(String corpNo);
}
