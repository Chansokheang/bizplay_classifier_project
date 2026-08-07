package com.api.bizplay_conversational.service.planPickerAgentService;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Business-plan picker sub-agent of the settlement flow. Given the user's free-text
 * description ("the Busan education trip", "부산 교육 출장 건") and the fetched plan
 * candidates, resolve WHICH plan they mean. Ladder: deterministic docNo/approvalId/title
 * match first, LLM ranking on the miss, null when genuinely ambiguous (caller re-shows chips).
 */
public interface PlanPickerAgentService {

    /**
     * @param message    the user's turn text
     * @param candidates array of {approvalId, docNo, title, startDate, endDate, purpose}
     * @return the matched approvalId, or null when no single plan is clearly meant
     */
    Long pick(String message, JsonNode candidates);
}
