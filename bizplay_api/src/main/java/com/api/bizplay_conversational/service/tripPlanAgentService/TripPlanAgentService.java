package com.api.bizplay_conversational.service.tripPlanAgentService;

import com.api.bizplay_conversational.model.request.TripPlanAgentRequest;
import com.api.bizplay_conversational.model.response.SessionDetailResponse;
import com.api.bizplay_conversational.model.response.SessionSummaryResponse;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface TripPlanAgentService {

    /** List existing sessions for a corp (newest first) so a sessionId can be found and reused. */
    List<SessionSummaryResponse> listSessions(String corpNo);

    /** Get a single session's full detail (draft + chat history) by id. */
    SessionDetailResponse getSession(String sessionId);

    /**
     * Save (overwrite) a session's draft so work can be paused and resumed later. The incoming draft
     * is re-normalized (shared route inherited, type/title defaulted) and re-validated (missing fields
     * recomputed, status flipped), then persisted. Returns the saved session detail.
     */
    SessionDetailResponse saveDraft(String sessionId, JsonNode draftJson);

    TripPlanAgentResponse chat(TripPlanAgentRequest request);
}
