package com.api.bizplay_conversational.service.travelerResolverService;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Resolves traveler NAMES (or a department reference) against the BizPlay corporation roster
 * (/popup/user/all). Deterministic matching first — userName, englishUserName, employeeNumber,
 * departmentName — with a small LLM assist ONLY for cross-script fuzzy cases (e.g. romanized
 * "Kim Doha" vs roster "김도하"). Exactly one match resolves; several matches (duplicates or a
 * department's members) come back as candidates for the user to confirm via chips; no match is
 * reported as not-found. The HTTP roster fetch itself is a gateway TOOL, not an agent.
 */
public interface TravelerResolverService {

    /** One roster hit. */
    record Candidate(long corporationUserId, String userName, String departmentName, String positionName) {
    }

    /** Result for ONE input (name or department). Exactly one of the three shapes is populated. */
    record Resolution(String input, Candidate matched, List<Candidate> candidates, boolean notFound) {
    }

    List<Resolution> resolve(List<String> inputs, JsonNode roster);
}
