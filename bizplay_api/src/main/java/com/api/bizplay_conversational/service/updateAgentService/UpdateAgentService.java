package com.api.bizplay_conversational.service.updateAgentService;

import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.DraftEditPlan;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Draft Update Agent: interprets a natural-language edit request ("change the dates to ...",
 * "remove Bob Martin", "set John's origin to Busan") against the CURRENT draft and returns a
 * validated list of edit operations. Data-only — it never writes draft_json; the RequestBody builder
 * applies the operations against a fixed allowlist.
 */
public interface UpdateAgentService {

    /**
     * @param message      the user's edit request
     * @param currentDraft the draft to edit (provides traveler names + current values for context)
     * @param history      prior conversation turns (may be null)
     * @return the planned edit operations (empty when nothing is a valid edit)
     */
    DraftEditPlan plan(String message, TripPlanDraft currentDraft, List<Message> history);
}
