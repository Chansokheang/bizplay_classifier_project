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

    /**
     * The corporation's registered travel destinations (출장지) — the same master the provider's
     * own Route Setup dialog offers, normalised to {id, name, address, sido}. This is what a
     * route leg's departureId / arrivalId and their addresses and coordinates come from.
     */
    com.fasterxml.jackson.databind.JsonNode routeOptions(String token);

    /**
     * Write each traveller's {@code bstrRoutes} into the draft NOW, so the preview shows the real
     * legs — distances, stops and total — while the user can still change them. The save-time
     * enrichment rebuilds them anyway; this only brings the same result forward.
     */
    void previewRoutes(ArrayNode documents, ObjectNode state, String token);

    /**
     * Read a travel route out of the user's own words against that master: departure, any number
     * of destinations in order, and the return point. Returns {points:[names…]} — names copied
     * exactly from the master — plus {@code traveller} when the sentence attributes the route to
     * ONE of the named travellers ("김도하는 …"), or null when the message names no route.
     */
    com.fasterxml.jackson.databind.JsonNode resolveRoutePoints(String message,
            java.util.List<String> travellerNames, String token, boolean korean);

}
