package com.api.bizplay_conversational.service.bizplaySettlementAgentService;

import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;

/**
 * Conversational settlement (출장정산) agent. Mirrors the plan agent's architecture —
 * rule-based orchestrator, chip-driven picks, session draft_json as the provider body —
 * but anchors every settlement to an EXISTING business-trip plan:
 *
 *   AWAIT_PERIOD          ask/parse the search window
 *   AWAIT_PLAN_PICK       ④ plan list -> user picks the trip to settle
 *   AWAIT_EVIDENCE_FILTER ⑤ plan detail imported -> ask evidence window + card types
 *   AWAIT_EVIDENCE_PICK   ⑥ unattached receipts -> user attaches evidence
 *   DONE                  receipts recorded in draft_json (no provider save yet)
 */
public interface BizplaySettlementAgentService {

    BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken);
}
