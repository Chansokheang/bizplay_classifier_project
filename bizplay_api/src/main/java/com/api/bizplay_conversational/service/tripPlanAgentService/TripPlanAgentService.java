package com.api.bizplay_conversational.service.tripPlanAgentService;

import com.api.bizplay_conversational.model.request.TripPlanAgentRequest;
import com.api.bizplay_conversational.model.response.SessionDetailResponse;
import com.api.bizplay_conversational.model.response.SessionSummaryResponse;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;

import java.util.List;

public interface TripPlanAgentService {

    /** List existing sessions for a corp (newest first) so a sessionId can be found and reused. */
    List<SessionSummaryResponse> listSessions(String corpNo);

    /** Get a single session's full detail (draft + chat history) by id. */
    SessionDetailResponse getSession(String sessionId);

    TripPlanAgentResponse chat(TripPlanAgentRequest request);
}
