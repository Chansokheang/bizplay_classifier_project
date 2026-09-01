package com.api.bizplay_conversational.service.destinationResolverAgentService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * Destination Resolver sub-agent — everything that turns a user's words into the provider's
 * region data (국가/도시 ids, the paper's allowed destination lists). It is a HYBRID by design,
 * benchmarked 20/22 vs 19/22 against an LLM-judges-everything variant (2026-08-31, qwen3-14b):
 *
 * <ol>
 *   <li><b>Deterministic first</b> — best-name match against the region master (exact beats
 *       containment, longer names beat substrings), zero LLM cost, exact by construction.</li>
 *   <li><b>Semantic judge second</b> — the LLM picks the meant destination when the words are
 *       not a name ("일본의 수도", a typo, another language), answering with the NAME copied
 *       verbatim so a wrong answer resolves to nothing rather than the wrong city.</li>
 *   <li><b>Negation veto</b> — a name matched out of a longer sentence is confirmed by the
 *       judge before it binds ("도쿄는 아니고 …" must not choose 도쿄).</li>
 * </ol>
 *
 * The region ids it returns always come from the provider's own APIs — the LLM never produces
 * an id, only selects among fetched names.
 */
public interface DestinationResolverAgentService {

    /**
     * The region the destination words mean under this paper's rules — a 시도, country, or city
     * node (city nodes carry {@code countryName}), or null when nothing resolves.
     */
    JsonNode resolveRegion(String destination, String bstrType, boolean regionUsed,
                           boolean policy, String token);

    /**
     * Deterministically match free text against the paper's allowed region lists — used to bind
     * the ANSWER to a destination ask without depending on the extractor noticing the place.
     * Returns {@code {name, countryName?}} or null when the text names no known region.
     */
    JsonNode resolveDestinationText(ArrayNode documents, String text, String token);

    /**
     * A typed COUNTRY name matched against the paper's own country list — half an answer to the
     * destination ask; returns {@code {name, countryCode, cities[]}} (its selectable cities,
     * empty-급지 falling back to the full list) or null when the text names no allowed country.
     */
    JsonNode resolveCountryText(ArrayNode documents, String text, String token);

    /**
     * The destination choices this purpose/segment's paper ACTUALLY allows, for the UI's
     * pickers: {@code {source: "policy"|"sido"|"countries"|"any"|"cities"|"unknown-form", …}}.
     */
    JsonNode destinationOptions(String purposeName, String segmentName,
                                Long purposeId, Long segmentId, String token);

    /** The full city list of one country, for the country→city cascade on non-policy forms. */
    JsonNode citiesOfCountry(String countryCode, String token);

    /**
     * Which listed destination the user's words MEAN — the LLM judge. Returns the 0-based index
     * into the options, or null when the message chooses none of them. With a single option this
     * doubles as the confirm/veto gate.
     */
    Integer pickDestination(List<String> options, String message, String context, boolean ko);

    /** The allowed-country names (policy list or full list), comma-joined, best-effort. */
    String allowedCountryNames(boolean policy, String token);
}
