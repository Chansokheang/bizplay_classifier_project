package com.api.bizplay_conversational.service.approvalLineService;

import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * The 결재선, shared by both agents: a 출장계획서 needs one, and BizPlay's own screen asks for one
 * again before a 출장정산서 is filed. The line lives on the session as
 * {@code state.approvalLines: [{corporationUserId, approvalKindType, name}]} — drafter excluded,
 * in order — and is applied to the document at create time.
 */
public interface ApprovalLineService {

    /** The corporation's people, as choices for the question. Null when the roster is unavailable. */
    List<TripPlanAgentResponse.PendingChoice> approverChoices(String token, boolean ko);

    /**
     * Apply one edit spoken in the conversation — add somebody, remove somebody, or swap one for
     * another — to {@code state.approvalLines}.
     *
     * @return the reply to send, or null when the message asked for no such edit (the caller then
     *         lets its normal flow handle the turn; nothing is ever changed on a guess)
     */
    String applyEdit(ObjectNode state, String message, String token, boolean ko, List<String> turns);
}
