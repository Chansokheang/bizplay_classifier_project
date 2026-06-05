package com.api.bizplay_conversational.service.planService;

import com.api.bizplay_conversational.model.request.PlanCreateRequest;
import com.api.bizplay_conversational.model.response.PlanResponse;

public interface PlanService {

    /** Create a plan from a full request body (the conversational draft_json, including sessionId). */
    PlanResponse create(PlanCreateRequest request);

    /**
     * Update the existing plan linked to the request's sessionId (AgentSessionId): re-writes the
     * conversational_trip_plan row and fully replaces its travelers and attachments from the body.
     */
    PlanResponse update(PlanCreateRequest request);
}
