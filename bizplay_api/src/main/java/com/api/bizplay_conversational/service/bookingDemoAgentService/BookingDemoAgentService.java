package com.api.bizplay_conversational.service.bookingDemoAgentService;

import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;

/**
 * DEMO ONLY — the booking agent we propose to build, backed by dummy inventory instead of a
 * provider. It exists to show that booking fits the SAME chat contract as the plan and settlement
 * agents (reply + pendingChoices + draftJson), so the existing UI renders it unchanged and the
 * traveller never leaves the conversation.
 *
 * <p>Nothing here touches BizPlay. When a real booking API (or a booking sub-agent) arrives, this
 * whole package plus {@code BookingDemoController} can be deleted and replaced without any other
 * file changing — that is the point of keeping it self-contained.
 */
public interface BookingDemoAgentService {

    /** One chat turn. {@code sessionId} null starts a new booking conversation. */
    BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request);

    /** Confirm the selected offer — the separate call that "spends money", never a chat turn. */
    BizplayPlanAgentResponse confirm(String sessionId);
}
