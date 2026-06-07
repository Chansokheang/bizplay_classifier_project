package com.api.bizplay_conversational.service.planService;

import com.api.bizplay_conversational.model.request.PlanCreateRequest;
import com.api.bizplay_conversational.model.response.PlanBatchDeleteResponse;
import com.api.bizplay_conversational.model.response.PlanResponse;

import java.util.List;

public interface PlanService {

    /** Create a plan from a full request body (the conversational draft_json, including sessionId). */
    PlanResponse create(PlanCreateRequest request);

    /** All business trip plans for a corp (newest first), each with travelers + attachments. */
    List<PlanResponse> getByCorpNo(String corpNo);

    /**
     * Plans for a corp filtered by approval_status (newest first), each with travelers + attachments.
     * 400 if approvalStatus is not one of the allowed values.
     */
    List<PlanResponse> getByApprovalStatus(String corpNo, String approvalStatus);

    /** A single business trip plan by its conversational_trip_plan id (with travelers + attachments). */
    PlanResponse getById(String id);

    /** Delete a plan by id, along with its travelers and attachments. 404 if it does not exist. */
    void deleteById(String id);

    /**
     * Delete several plans by id (each with its travelers and attachments). Invalid id formats -> 400;
     * ids that don't exist are reported as notFound rather than failing. Returns a delete summary.
     */
    PlanBatchDeleteResponse deleteByIds(List<String> ids);

    /**
     * Update only the approval_status of a plan (by id). The status must be one of the allowed
     * values; 400 on an invalid status, 404 if the plan does not exist. Returns the updated plan.
     */
    PlanResponse updateApprovalStatus(String id, String approvalStatus);

    /**
     * Update the existing plan linked to the request's sessionId (AgentSessionId): re-writes the
     * conversational_trip_plan row and fully replaces its travelers and attachments from the body.
     */
    PlanResponse update(PlanCreateRequest request);
}
