package com.api.bizplay_conversational.service.bizplayPlanAgentService;

import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Orchestrator of the BizPlay form-driven trip-plan flow. Per turn it delegates to specialized
 * sub-agents — Purpose & Segment resolution, deterministic form loading (② → ③-shaped skeleton),
 * LLM field mapping + deterministic writers, and follow-up questions — with human-in-the-loop
 * chips for the trip-type choice and the final review before saving.
 */
public interface BizplayPlanAgentService {

    BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken);

    /**
     * "Create this plan": validate the session's draft (required fields filled) and POST its
     * draft_json AS-IS to BizPlay (③, DRAFT_ONLY). On success the session becomes POSTED and the
     * provider's response text (e.g. "작성되었습니다.") is returned.
     *
     * @param approvalLines optional lines picked in the "Set approval order" step — an array of
     *                      {@code {corporationUserId, approvalKindType?}} appended after the
     *                      drafter's DRAFT line on the master document; null/empty keeps the
     *                      DRAFT-only save.
     */
    BizplayPlanAgentResponse createPlan(String sessionId, String corpNo, String bizplayToken,
                                        JsonNode approvalLines);

    /**
     * Manual (non-chat) create: rebuild the ③-shaped draft from the retrieved form skeleton,
     * write the request's values through the SAME writer paths the agent uses, fan out one
     * document per traveler, validate required fields, and POST to BizPlay.
     */
    BizplayPlanAgentResponse createManualPlan(
            com.api.bizplay_conversational.model.request.BizplayManualPlanRequest request,
            String bizplayToken);
}
