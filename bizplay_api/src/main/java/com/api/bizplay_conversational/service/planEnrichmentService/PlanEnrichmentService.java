package com.api.bizplay_conversational.service.planEnrichmentService;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Fills the parts of a plan draft that the provider's own screen fills from lookup APIs and the
 * chat flow used to leave empty (see docs/bstr-plan-save-api-guide.md):
 *
 * <ul>
 *   <li><b>국가/도시</b> — the region id that rides {@code BSTR_PERIOD.selections[].selectionId}.
 *       Which id (시도 / 국가 / 도시) is decided by the paper's {@code bstrType} +
 *       {@code bstrRegionUsed}; policy mode ({@code bstrPolicyRegionUsed}) restricts the
 *       choices to the 급지-registered lists.</li>
 *   <li><b>기간 시각</b> — {@code bstrPeriodTimeUsed} papers store "yyyy-MM-dd HH:mm".</li>
 *   <li><b>이동경로</b> — the per-traveler {@code bstrRoutes} legs, with coordinates and distance
 *       from the destination master / TMap bypass, when the paper carries a BSTR_ROUTE item.</li>
 * </ul>
 *
 * Forms whose flags demand none of this (e.g. DOMESTIC without region and without a route item)
 * are left completely untouched — the needed lookups are decided per paper, not globally.
 */
public interface PlanEnrichmentService {

    /**
     * Enrich {@code documents[0]} in place. Returns human-readable notes about what was filled
     * or skipped (appended to the create reply). Throws {@link IllegalArgumentException} when
     * the paper REQUIRES a region and the trip's destination cannot be resolved to one — saving
     * would produce a document the provider considers broken and hard to fix afterwards.
     */
    List<String> enrich(ArrayNode documents, ObjectNode state, String token, boolean ko);

    /**
     * The ONE follow-up question the lookup-driven requirements still need, or null when the
     * draft would enrich cleanly. Asked at the "form is complete" moment of the CHAT — a region
     * outside the paper's allowed lists, or a missing transport type on a route-carrying form —
     * so the user hears about it while they can still answer, instead of as a refusal at create.
     * Returned as {@code {"kind": "region"|"transport", "text": "..."}} so the caller can
     * surface the right UI (the region ask re-opens the country/city dropdown) without
     * matching the message wording.
     */
    com.fasterxml.jackson.databind.JsonNode readinessAsk(ArrayNode documents, ObjectNode state,
            String token, boolean ko);

}
