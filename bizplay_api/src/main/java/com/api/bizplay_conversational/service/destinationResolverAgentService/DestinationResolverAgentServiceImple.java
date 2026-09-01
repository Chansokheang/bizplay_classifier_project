package com.api.bizplay_conversational.service.destinationResolverAgentService;

import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationResolverAgentServiceImple implements DestinationResolverAgentService {

    private final BizplayGatewayService bizplayGatewayService;
    private final com.api.bizplay_conversational.config.BizplayProperties bizplayProperties;
    private final ObjectMapper objectMapper;
    private final com.api.bizplay_conversational.service.slotFillerAgentService.SlotFillerAgentService slotFillerAgentService;

    @Override
    public JsonNode resolveDestinationText(ArrayNode documents, String text, String token) {
        if (documents == null || documents.isEmpty() || !documents.get(0).isObject()
                || text == null || text.isBlank()) {
            return null;
        }
        JsonNode paper = planPaper((ObjectNode) documents.get(0), token);
        if (paper == null) {
            return null;
        }
        String bstrType = paper.path("bstrType").asText("");
        boolean regionUsed = paper.path("bstrRegionUsed").asBoolean(false);
        boolean policy = paper.path("bstrPolicyRegionUsed").asBoolean(false);
        if (!("OVERSEA".equals(bstrType) || regionUsed)) {
            return null;
        }
        JsonNode region = resolveRegion(text.trim(), bstrType, regionUsed, policy, token);
        if (region == null) {
            return null;
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.put("name", region.path("name").asText(""));
        out.put("countryName", region.path("countryName").asText(""));
        return out;
    }

    @Override
    public JsonNode resolveCountryText(ArrayNode documents, String text, String token) {
        // A typed COUNTRY at the destination ask ("스위스로 가요") is half an answer, not a
        // wrong one — the caller asks which of ITS cities. Matched against the paper's own
        // country list (policy or full), like everything else here.
        if (documents == null || documents.isEmpty() || !documents.get(0).isObject()
                || text == null || text.isBlank()) {
            return null;
        }
        JsonNode paper = planPaper((ObjectNode) documents.get(0), token);
        if (paper == null || !"OVERSEA".equals(paper.path("bstrType").asText(""))) {
            return null;
        }
        boolean policy = paper.path("bstrPolicyRegionUsed").asBoolean(false);
        JsonNode countries = policy
                ? bizplayGatewayService.getUsedRegionList("COUNTRY", token)
                : bizplayGatewayService.getRegionList("COUNTRY", token);
        JsonNode hit = matchByName(countries, text.trim());
        if (hit == null) {
            return null;
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.put("name", hit.path("name").asText(""));
        out.put("countryCode", hit.path("countryCode").asText(""));
        // Its selectable cities (policy list, with the empty-list=all-cities fallback).
        ArrayNode cities = out.putArray("cities");
        for (JsonNode city : safeArray(citiesOf(hit, policy, countries, token))) {
            cities.add(city.path("name").asText("?"));
            if (cities.size() >= 60) {
                break;
            }
        }
        return out;
    }

    @Override
    public JsonNode destinationOptions(String purposeName, String segmentName,
                                       Long purposeId, Long segmentId, String token) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("source", "any");
        ArrayNode regions = out.putArray("regions");
        try {
            // Precedence: the NAMES describe what the user is choosing RIGHT NOW in the wizard;
            // the ids come from the agent draft, which may belong to an earlier conversation.
            // Preferring ids leaked a stale 장기 form's policy list into a fresh 일반 flow.
            JsonNode paper = paperByNames(purposeName, segmentName, token);
            if (paper == null && purposeId != null && purposeId > 0) {
                // Typed conversations never fill the wizard's purpose field — fall back to the
                // draft's exact ids.
                JsonNode papers = bizplayGatewayService.getPapers(purposeId, segmentId, token);
                for (JsonNode pp : safeArray(papers)) {
                    if ("BSTR_PLAN".equals(pp.path("paperKind").path("paperKindType").asText(""))) {
                        paper = pp;
                    }
                }
            }
            if (paper == null) {
                out.put("source", "unknown-form");
                return out;
            }
            String bstrType = paper.path("bstrType").asText("");
            boolean regionUsed = paper.path("bstrRegionUsed").asBoolean(false);
            boolean policy = paper.path("bstrPolicyRegionUsed").asBoolean(false);
            if ("DOMESTIC".equals(bstrType) && regionUsed) {
                out.put("source", "sido");
                JsonNode sidos = policy
                        ? bizplayGatewayService.getUsedRegionList("SIDO", token)
                        : bizplayGatewayService.getRegionList("SIDO", token);
                for (JsonNode r : safeArray(sidos)) {
                    regions.add(r.path("name").asText("?"));
                }
            } else if ("OVERSEA".equals(bstrType) && !policy && !regionUsed) {
                // Country-level form without a policy list: the COUNTRY is the saved region, so
                // the full country master is the choice list — terminal, no city step.
                out.put("source", "countries-flat");
                for (JsonNode c : safeArray(bizplayGatewayService.getRegionList("COUNTRY", token))) {
                    regions.add(c.path("name").asText("?"));
                }
            } else if ("OVERSEA".equals(bstrType) && !policy) {
                // Country → city two-step without a policy list: hand back the country master;
                // the UI fetches the picked country's cities via ?citiesOf={countryCode}.
                out.put("source", "countries");
                ArrayNode countries = out.putArray("countries");
                for (JsonNode c : safeArray(bizplayGatewayService.getRegionList("COUNTRY", token))) {
                    ObjectNode e = countries.addObject();
                    e.put("name", c.path("name").asText("?"));
                    e.put("countryCode", c.path("countryCode").asText(""));
                    e.put("nameEn", c.path("nameEn").asText(""));
                }
            } else if ("OVERSEA".equals(bstrType) && policy) {
                // The 급지 lists are the ACTUAL allowed set — chips must show these, not a
                // generic country list. Countries with a specific city list get their cities
                // (that's what selectionId ultimately needs); "all cities" countries show as
                // the country itself.
                out.put("source", "policy");
                JsonNode countries = bizplayGatewayService.getUsedRegionList("COUNTRY", token);
                for (JsonNode c : safeArray(countries)) {
                    JsonNode cities = bizplayGatewayService.getUsedRegionCities(
                            c.path("countryCode").asText(""), token);
                    if (cities == null || !cities.isArray() || cities.isEmpty()) {
                        // Registered country with an EMPTY 급지 city list = ALL its cities are
                        // allowed (the guide's disambiguation rule, exactly as citiesOf() and
                        // resolveRegion() read it). Offer the full list — a bare country row
                        // produced a pick ("스위스") the resolver then rightly refused, because
                        // the saved selectionId must be a CITY id.
                        cities = bizplayGatewayService.getRegionCities(
                                c.path("countryCode").asText(""), token);
                    }
                    if (cities != null && cities.isArray() && !cities.isEmpty()) {
                        int added = 0;
                        for (JsonNode city : cities) {
                            regions.add(c.path("name").asText("?") + " · " + city.path("name").asText("?"));
                            if (++added >= 60) {
                                break;   // a dropdown holds the list, but keep an upper bound
                            }
                        }
                    } else {
                        regions.add(c.path("name").asText("?"));
                    }
                }
            }
            // OVERSEA without policy: any country/city goes — "any" with an empty list; the UI
            // shows a plain typed ask.
        } catch (Exception e) {
            // Surface, never swallow: the UI shows this message verbatim so a broken region
            // lookup is debuggable instead of silently degrading.
            log.warn("destination options lookup failed: {}", e.getMessage());
            throw new IllegalStateException("destination-options lookup failed: " + e.getMessage(), e);
        }
        return out;
    }

    /** The BSTR_PLAN paper matched from purpose/segment NAMES via the purpose catalog. */
    private JsonNode paperByNames(String purposeName, String segmentName, String token) {
        if (purposeName == null || purposeName.isBlank()) {
            return null;
        }
        JsonNode catalog = bizplayGatewayService.getPurposeCatalog(
                bizplayProperties.getDefaultCorpUserId(), token);
        for (JsonNode p : safeArray(catalog)) {
            if (!purposeName.trim().equals(p.path("purpose").asText(""))) {
                continue;
            }
            Long segId = null;
            if (segmentName != null && !segmentName.isBlank()) {
                for (JsonNode s : p.path("segments")) {
                    if (segmentName.trim().equals(s.path("segment").asText(""))) {
                        segId = s.path("id").asLong();
                    }
                }
            }
            if (segId == null && p.path("segments").size() > 0) {
                segId = p.path("segments").get(0).path("id").asLong();
            }
            JsonNode papers = bizplayGatewayService.getPapers(p.path("id").asLong(), segId, token);
            for (JsonNode pp : safeArray(papers)) {
                if ("BSTR_PLAN".equals(pp.path("paperKind").path("paperKindType").asText(""))) {
                    return pp;
                }
            }
        }
        return null;
    }

    @Override
    public JsonNode citiesOfCountry(String countryCode, String token) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("source", "cities");
        ArrayNode regions = out.putArray("regions");
        ArrayNode regionsEn = out.putArray("regionsEn");
        for (JsonNode c : safeArray(bizplayGatewayService.getRegionCities(countryCode, token))) {
            regions.add(c.path("name").asText("?"));
            regionsEn.add(c.path("nameEn").asText(""));
        }
        return out;
    }

    @Override
    public Integer pickDestination(java.util.List<String> options, String message,
                                   String context, boolean ko) {
        if (options == null || options.isEmpty() || message == null || message.isBlank()) {
            return null;
        }
        // The judge answers with the NAME copied verbatim, never a row number: counting rows in
        // a 53-entry list is exactly where a model slips ("수도" once came back as row 51 =
        // 후쿠오카), while a copied name is self-verifying — anything not in the list resolves
        // to nothing instead of to the wrong city.
        StringBuilder rows = new StringBuilder();
        for (String o : options) {
            rows.append(o).append("; ");
        }
        try {
            String verdict = slotFillerAgentService.extract(message, java.util.Map.of(
                    "destName", "Situation: "
                            + (context == null || context.isBlank() ? "" : context + " ")
                            + "The assistant listed these destinations and asked the user to "
                            + "choose one: " + rows + "Judge THIS message SEMANTICALLY: which "
                            + "single listed destination does it mean? Match by name in any "
                            + "language or spelling, or by description (a country's capital, a "
                            + "famous landmark's city, 'the biggest city of …'). Answer with that "
                            + "destination's name COPIED EXACTLY from the list. Omit the field "
                            + "when the message does not refer to any of them"), ko)
                    .path("destName").asText("").trim();
            log.info("[ENRICH] semantic destination pick on '{}': '{}'",
                    message.length() > 40 ? message.substring(0, 40) : message,
                    verdict.isEmpty() ? "none" : verdict);
            if (!verdict.isEmpty()) {
                String v = verdict.toLowerCase(Locale.ROOT);
                for (int i = 0; i < options.size(); i++) {
                    String full = options.get(i).toLowerCase(Locale.ROOT);
                    String namePart = full.contains(" (") ? full.substring(0, full.indexOf(" (")) : full;
                    if (full.equals(v) || namePart.equals(v)
                            || (v.length() >= 2 && (namePart.equals(v) || full.startsWith(v + " (")
                                    || full.contains("(" + v + ")")))) {
                        return i;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("semantic destination pick failed: {}", e.getMessage());
        }
        return null;
    }

    /** The allowed-country names (policy list or full list), comma-joined, best-effort. */
    @Override
    public String allowedCountryNames(boolean policy, String token) {
        try {
            JsonNode countries = policy
                    ? bizplayGatewayService.getUsedRegionList("COUNTRY", token)
                    : bizplayGatewayService.getRegionList("COUNTRY", token);
            List<String> names = new ArrayList<>();
            for (JsonNode c : safeArray(countries)) {
                names.add(c.path("name").asText("?"));
                if (names.size() >= 8) {
                    break;
                }
            }
            return String.join(", ", names);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The region row the destination name resolves to under this paper's rules, or null.
     * DOMESTIC+region → 시도; OVERSEA without region → 국가; OVERSEA with region → 도시 (the
     * city id is the ONLY thing saved — the guide's §1.4). Policy mode searches the
     * 급지-registered lists, with the guide's empty-list fallback rule.
     */
    @Override
    public JsonNode resolveRegion(String destination, String bstrType, boolean regionUsed,
                                   boolean policy, String token) {
        if (destination.isBlank()) {
            return null;
        }
        if ("DOMESTIC".equals(bstrType)) {
            log.info("[ENRICH] GET bstr/area/region{}/SIDO — matching '{}'",
                    policy ? "/used" : "", destination);
            JsonNode sidos = policy
                    ? bizplayGatewayService.getUsedRegionList("SIDO", token)
                    : bizplayGatewayService.getRegionList("SIDO", token);
            return matchByName(sidos, destination);
        }
        log.info("[ENRICH] GET bstr/area/region{}/COUNTRY — resolving '{}'",
                policy ? "/used" : "", destination);
        JsonNode countries = policy
                ? bizplayGatewayService.getUsedRegionList("COUNTRY", token)
                : bizplayGatewayService.getRegionList("COUNTRY", token);
        if (!regionUsed) {
            JsonNode direct = matchByName(countries, destination);
            if (direct != null) {
                return direct;
            }
            // The user usually names the CITY ("오사카"), not the country. Find the BEST
            // city match across the allowed countries and answer with ITS country.
            JsonNode bestC = null;
            int bestCScore = 0;
            for (JsonNode c : safeArray(countries)) {
                JsonNode city = matchByName(citiesOf(c, policy, countries, token), destination);
                if (city != null && matchScore(city, destination) > bestCScore) {
                    bestCScore = matchScore(city, destination);
                    bestC = c;
                }
            }
            return bestC == null ? null : withCountryName(bestC, bestC.path("name").asText(""));
        }
        // OVERSEA + region: the saved id must be a CITY id from the per-country city lists —
        // again the BEST match across every country, not the first country with any hit.
        JsonNode bestCity = null;
        JsonNode bestCountry = null;
        int bestScore = 0;
        for (JsonNode c : safeArray(countries)) {
            JsonNode city = matchByName(citiesOf(c, policy, countries, token), destination);
            if (city != null && matchScore(city, destination) > bestScore) {
                bestScore = matchScore(city, destination);
                bestCity = city;
                bestCountry = c;
            }
        }
        return bestCity == null ? null
                : withCountryName(bestCity, bestCountry.path("name").asText(""));
    }

    /**
     * The resolved node plus the COUNTRY it belongs to — as a copy, because the source
     * node lives in the gateway's cached list and must never be mutated. The country name
     * only exists at match time (the loop knows which country's city list hit), and the
     * UI's preview card wants it ("Country: 일본 / City: 도쿄").
     */
    private ObjectNode withCountryName(JsonNode node, String countryName) {
        ObjectNode out = node.deepCopy();
        out.put("countryName", countryName);
        return out;
    }

    /**
     * The selectable cities of one country. Policy mode reads the 급지 list first; an EMPTY 급지
     * city list for a country that IS 급지-registered means "all cities allowed" (the guide's
     * disambiguation rule), so it falls back to the full list.
     */
    private JsonNode citiesOf(JsonNode country, boolean policy, JsonNode usedCountries, String token) {
        String cc = country.path("countryCode").asText("");
        if (cc.isBlank()) {
            return objectMapper.createArrayNode();
        }
        if (!policy) {
            log.info("[ENRICH] GET bstr/area/region/city/{} — full city list", cc);
            return bizplayGatewayService.getRegionCities(cc, token);
        }
        log.info("[ENRICH] GET bstr/area/region/used/CITY/{} — policy city list", cc);
        JsonNode used = bizplayGatewayService.getUsedRegionCities(cc, token);
        if (used != null && used.isArray() && !used.isEmpty()) {
            return used;
        }
        for (JsonNode u : safeArray(usedCountries)) {
            if (cc.equals(u.path("countryCode").asText(""))) {
                return bizplayGatewayService.getRegionCities(cc, token);   // registered, all cities
            }
        }
        return objectMapper.createArrayNode();                             // not registered at all
    }

    /**
     * Loose two-way name match over name / nameEn — "오사카" finds "오사카", "tokyo" finds
     * "Tokyo". Returns the BEST match, not the first: free text like "일본 오사카로 가요"
     * contains both 오사카 and (inside "오사카로") the Myanmar city 카로 — an exact-name hit
     * outranks containment, and a longer name outranks a shorter one.
     */
    private JsonNode matchByName(JsonNode list, String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return null;
        }
        JsonNode best = null;
        int bestScore = 0;
        for (JsonNode r : safeArray(list)) {
            int score = matchScore(r, wanted);
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    /**
     * Text contains the name starting at a WORD boundary — "일본 오사카로" holds 오사카, but
     * the surname in "김도하" must never surface 도하 (Doha): a letter immediately before
     * the name means it is the tail of another word (a person's name, a compound), not a
     * place. Typo'd prefixed forms ("오오사카") fall to the semantic judge instead.
     */
    private boolean wordStartContains(String text, String name) {
        int idx = text.indexOf(name);
        while (idx >= 0) {
            if (idx == 0 || !Character.isLetter(text.charAt(idx - 1))) {
                return true;
            }
            idx = text.indexOf(name, idx + 1);
        }
        return false;
    }

    /** 0 = no match; containment scores the matched name's length; exact equality tops all. */
    private int matchScore(JsonNode r, String wanted) {
        String w = wanted.trim().toLowerCase(Locale.ROOT);
        int best = 0;
        for (String key : new String[]{"name", "nameEn"}) {
            String n = r.path(key).asText("").trim().toLowerCase(Locale.ROOT);
            if (n.isEmpty()) {
                continue;
            }
            int score = 0;
            if (n.equals(w)) {
                score = 1000 + n.length();
            } else if (n.length() >= 2 && w.length() >= 2
                    && (n.contains(w) || wordStartContains(w, n))) {
                score = n.length();
            }
            if (score > best) {
                best = score;
            }
        }
        return best;
    }

    /** The BSTR_PLAN paper this document rides on, fetched fresh so the flags are current. */
    private JsonNode planPaper(ObjectNode doc, String token) {
        long purposeId = doc.path("bstrPurposeId").asLong(0);
        Long segmentId = doc.hasNonNull("bstrSegmentId") ? doc.path("bstrSegmentId").asLong() : null;
        if (purposeId <= 0) {
            return null;
        }
        log.info("[ENRICH] GET paper/purpose/{}{} — reading the form's region/route flags",
                purposeId, segmentId == null ? "" : "?segmentId=" + segmentId);
        JsonNode papers = bizplayGatewayService.getPapers(purposeId, segmentId, token);
        if (papers != null && papers.isArray()) {
            for (JsonNode p : papers) {
                if ("BSTR_PLAN".equals(p.path("paperKind").path("paperKindType").asText(""))) {
                    return p;
                }
            }
        }
        return null;
    }

    private ArrayNode safeArray(JsonNode n) {
        return n != null && n.isArray() ? (ArrayNode) n : objectMapper.createArrayNode();
    }
}
