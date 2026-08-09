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
}
