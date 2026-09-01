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
    /**
     * Record a turn the CLIENT handled locally (approval-line picks, save commands) into the
     * session transcript — no pipeline, no side effects. Every user prompt must reach the
     * agent's history even when the UI answered it, so later context ("그 사람은 빼줘")
     * resolves and the API-side transcript stays complete.
     */
    void noteTurn(String sessionId, String corpNo, String userText, String assistantText);

    /**
     * LLM intent judge for the client-side approval-line step — NO word lists. Given the
     * user's message and the step's context (who can be picked, whose role is pending,
     * whether the save question was just asked), the model answers what the user MEANS:
     * pick_person / assign_role / no_more / save_now / not_yet / remove_person / other.
     * Values are validated against the roster and the role enum before returning.
     */
    com.fasterxml.jackson.databind.JsonNode approvalIntent(String corpNo,
            com.fasterxml.jackson.databind.JsonNode body);

    BizplayPlanAgentResponse createManualPlan(
            com.api.bizplay_conversational.model.request.BizplayManualPlanRequest request,
            String bizplayToken);
}
