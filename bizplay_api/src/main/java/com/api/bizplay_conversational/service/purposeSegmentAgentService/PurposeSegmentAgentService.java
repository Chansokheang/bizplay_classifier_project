package com.api.bizplay_conversational.service.purposeSegmentAgentService;

import com.api.bizplay_conversational.model.response.PurposeOption;
import com.api.bizplay_conversational.model.response.PurposeResolutionResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Purpose & Segment sub-agent: understands the user's message and matches it against the corp's
 * ACTUAL Travel-Purpose × Trip-Type catalog (①). Data-only — it never touches draft_json. A single
 * confident match resolves directly; anything ambiguous returns candidates for the user to pick
 * (rendered as chips, human-in-the-loop).
 */
public interface PurposeSegmentAgentService {

    /** Flatten the ① catalog into selectable purpose×segment options (activated only). */
    List<PurposeOption> flattenCatalog(JsonNode catalog);

    /**
     * Resolve the user's message against the options. Returns a resolved option, or candidates when
     * ambiguous / no match (candidates = best guesses, or all options when the agent has no idea).
     */
    PurposeResolutionResult resolve(String message, List<PurposeOption> options);
}
