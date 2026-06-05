package com.api.bizplay_conversational.service.clarificationAgentService;

import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.MissingFieldsResult;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Clarification / Field-Completion sub-agent: turns the deterministic missing-field report into a
 * single, natural-language follow-up question (or a "ready to create" confirmation when nothing is
 * missing). This is what drives the multi-turn loop toward approval.
 */
public interface ClarificationAgentService {

    /**
     * Compose the assistant's follow-up sentence for this turn.
     *
     * @param draft   the current accumulated draft (for context: what is already known)
     * @param missing the deterministic missing-field result
     * @param history prior conversation turns (for tone/context; may be empty)
     * @return a short follow-up question listing what is still needed, or a confirmation prompt when complete
     */
    String composeFollowUp(TripPlanDraft draft, MissingFieldsResult missing, List<Message> history);
}
