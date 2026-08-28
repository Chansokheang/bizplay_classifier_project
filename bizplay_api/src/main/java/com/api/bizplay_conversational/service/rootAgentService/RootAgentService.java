package com.api.bizplay_conversational.service.rootAgentService;

import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The root agent: ONE chat that decides which specialist should answer — the trip-plan agent
 * (출장계획서), the settlement agent (출장정산서) or the booking agent (demo) — and keeps the user
 * in that flow until they move on.
 *
 * <p>It owns no domain logic of its own. Each turn is delegated verbatim to a child agent and the
 * child's answer is returned unchanged apart from the session id, so nothing about the three
 * existing agents had to change for this to exist.
 */
public interface RootAgentService {

    /** One chat turn. Routes, delegates, and answers as whichever agent took the turn. */
    BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken);

    /**
     * Finish whatever the active agent is building — file the plan, submit the settlement, or
     * confirm the booking. The caller does not need to know which: the root session remembers.
     */
    BizplayPlanAgentResponse create(String sessionId, String corpNo, String bizplayToken,
                                    JsonNode approvalLines);
}
