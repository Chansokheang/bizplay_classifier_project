package com.api.bizplay_conversational.service.planEnrichmentService;

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
public class PlanEnrichmentServiceImple implements PlanEnrichmentService {

    private final BizplayGatewayService bizplayGatewayService;
    private final ObjectMapper objectMapper;
    private final com.api.bizplay_conversational.config.BizplayProperties bizplayProperties;
    private final com.api.bizplay_conversational.service.slotFillerAgentService.SlotFillerAgentService slotFillerAgentService;
    private final com.api.bizplay_conversational.service.destinationResolverAgentService.DestinationResolverAgentService destinationResolverAgentService;

    @Override
    public List<String> enrich(ArrayNode documents, ObjectNode state, String token, boolean ko) {
        List<String> notes = new ArrayList<>();
        if (documents == null || documents.isEmpty() || !documents.get(0).isObject()) {
            return notes;
        }
        // ONE DOCUMENT PER TRAVELLER — and every one of them needs the region on its period
        // rows and its own bstrRoutes legs. Enriching only the first filed the second traveller's
        // document with no region and no route at all.
        for (int i = 0; i < documents.size(); i++) {
            if (!documents.get(i).isObject()) {
                continue;
            }
            // documents[] fans out in travelerIds order, so index i is that traveller's copy —
            // which is how a per-traveller route finds the document it belongs to.
            String who = state.path("travelers").path(i).asText("");
            List<String> docNotes = enrichDocument((ObjectNode) documents.get(i), state, token, ko, who);
            if (i == 0) {
                notes.addAll(docNotes);   // the notes describe the trip, not each traveller's copy
            }
        }
        return notes;
    }

    /** Everything the provider's own screen adds, for ONE traveller's document. */
    private List<String> enrichDocument(ObjectNode doc, ObjectNode state, String token, boolean ko,
                                        String travellerName) {
        List<String> notes = new ArrayList<>();
        JsonNode paper = planPaper(doc, token);
        if (paper == null) {
            log.warn("Plan paper {} not found while enriching — leaving the draft as-is.",
                    doc.path("paperId").asLong());
            return notes;
        }
        // The guide's §0 warning: bstrStartDate/bstrEndDate must be "yyyy-MM-dd" STRINGS. A date
        // serialized as ISO-UTC ("...T00:00:00.000Z") gets timezone-shifted by the server — an
        // evening save moves the trip to the next day. Trim any ISO tail before the POST.
        for (String k : new String[]{"bstrStartDate", "bstrEndDate"}) {
            String v = doc.path(k).asText("");
            if (v.length() > 10 && v.charAt(10) == 'T') {
                doc.put(k, v.substring(0, 10));
            }
        }

        String bstrType = paper.path("bstrType").asText("");
        boolean regionUsed = paper.path("bstrRegionUsed").asBoolean(false);
        boolean policy = paper.path("bstrPolicyRegionUsed").asBoolean(false);
        boolean timeUsed = paper.path("bstrPeriodTimeUsed").asBoolean(false);
        JsonNode routeItem = paperItem(paper, "BSTR_ROUTE");

        // ① 국가/도시 → BSTR_PERIOD selections[].selectionId — only when this paper wants it.
        boolean regionNeeded = "OVERSEA".equals(bstrType) || regionUsed;
        if (regionNeeded) {
            // The company screen repeats the Trip Period/국가 row PER DAY ("출장 일자별 시간
            // 반복") — each day carries its own datetimes and its own country/city id. The
            // chat writer's single start~end row is normalized to that shape before writing.
            rebuildPeriodRowsPerDay(doc);
            String destination = state.path("destination").asText("").trim();
            JsonNode region = destinationResolverAgentService.resolveRegion(destination, bstrType, regionUsed, policy, token);
            if (region == null) {
                // Saving anyway would persist a document with no region — the exact broken data
                // the provider warned is hard to fix later. Refuse with the allowed choices.
                throw new IllegalArgumentException(regionAskMessage(destination, bstrType,
                        regionUsed, policy, token, ko));
            }
            long regionId = region.path("id").asLong();
            String label = region.path("name").asText("");
            if (!region.path("countryName").asText("").isBlank()) {
                state.put("destinationCountry", region.path("countryName").asText(""));
            }
            log.info("[ENRICH] region resolved: '{}' -> {} (id {}) — writing selectionId",
                destination, label, regionId);
            // The company form allows a DIFFERENT country/city (and memo) per period row —
            // day 1 가봉/랑바레네, day 2 가이아나/티메리. Per-day assignments captured in the
            // chat override the trip-wide destination on their rows.
            java.util.Map<String, ObjectNode> dayOverrides = new java.util.LinkedHashMap<>();
            JsonNode pp = state.path("periodPlaces");
            if (pp.isObject()) {
                pp.fields().forEachRemaining(e -> {
                    String place = e.getValue().path("place").asText("");
                    JsonNode r = destinationResolverAgentService.resolveRegion(
                            place, bstrType, regionUsed, policy, token);
                    if (r != null) {
                        ObjectNode o = objectMapper.createObjectNode();
                        o.put("id", r.path("id").asLong());
                        o.put("label", r.path("name").asText(""));
                        o.put("memo", e.getValue().path("detail").asText(""));
                        dayOverrides.put(e.getKey(), o);
                    } else {
                        log.warn("[ENRICH] per-day place '{}' for {} did not resolve — that "
                                + "row keeps the trip-wide destination.", place, e.getKey());
                    }
                });
            }
        int rows = writeSelectionId(doc, regionId, label,
                state.path("destinationDetail").asText("").trim(), dayOverrides);
            notes.add(t(ko, "Region \"" + label + "\" (id " + regionId + ") saved on " + rows
                            + " period row(s).",
                    "지역 \"" + label + "\"(id " + regionId + ")을 출장기간 " + rows
                            + "개 행에 저장했어요."));
        }

        // ② bstrPeriodTimeUsed papers store "yyyy-MM-dd HH:mm" in the period selections.
        if (timeUsed) {
            appendPeriodTimes(doc);
        }

        // The mapper occasionally echoes the trip period into the BSTR_ROUTE issued item's
        // selections — a slot that carries NO route data (the guide's §2: routes live in
        // bstrRoutes). Strip such junk so the provider never stores dates in a route item.
        for (JsonNode issued : doc.withArray("issuedItems")) {
            if ("BSTR_ROUTE".equals(issued.path("item").path("itemType").asText(""))
                    && issued.path("selections").size() > 0) {
                ((ObjectNode) issued).putArray("selections");
                log.info("[ENRICH] stripped echoed selections from the BSTR_ROUTE issued item");
            }
        }

        // ③ 이동경로 → bstrRoutes — only when the paper carries the item at all.
        if (routeItem != null && routeItem.path("used").asBoolean(true)) {
            boolean required = routeItem.path("required").asBoolean(false);
            JsonNode itemDto = routeItem.path("itemDto");
            boolean calcDistance = itemDto.path("calcDistanceUsed").asBoolean(true);
            int legs = buildRoutes(doc, state, bstrType, calcDistance, token, travellerName);
            if (legs > 0) {
                notes.add(t(ko, "Travel route saved (" + legs + " leg(s)).",
                        "이동경로 " + legs + "개 구간을 저장했어요."));
            } else if (required) {
                notes.add(t(ko, "Warning: this form requires a travel route but no origin/"
                                + "destination could be resolved — the route was left empty.",
                        "주의: 이 양식은 이동경로가 필수지만 출발지/목적지를 확인하지 못해 "
                                + "경로가 비어 있습니다."));
            }
        }
        return notes;
    }

    @Override
    public JsonNode readinessAsk(ArrayNode documents, ObjectNode state, String token, boolean ko) {
        if (documents == null || documents.isEmpty() || !documents.get(0).isObject()) {
            return null;
        }
        ObjectNode doc = (ObjectNode) documents.get(0);
        JsonNode paper = planPaper(doc, token);
        if (paper == null) {
            return null;
        }
        String bstrType = paper.path("bstrType").asText("");
        boolean regionUsed = paper.path("bstrRegionUsed").asBoolean(false);
        boolean policy = paper.path("bstrPolicyRegionUsed").asBoolean(false);
        // ① The destination must land in the paper's allowed region lists — asked here, with the
        // allowed choices, while the user can still change it.
        if ("OVERSEA".equals(bstrType) || regionUsed) {
            String destination = state.path("destination").asText("").trim();
            JsonNode resolved = destinationResolverAgentService.resolveRegion(destination, bstrType, regionUsed, policy, token);
            if (resolved != null && !resolved.path("countryName").asText("").isBlank()) {
                // Remembered for the reply: the save body has no country field (the city id
                // implies it), but the preview card shows "Country / City" as two rows.
                state.put("destinationCountry", resolved.path("countryName").asText(""));
            }
            if (resolved == null) {
                // No destination at all: this is the FIRST time the trip's where is asked —
                // a plain question, not a "your value isn't on the list" complaint.
                if (destination.isBlank()) {
                    String choices = policy
                            ? destinationResolverAgentService.allowedCountryNames(true, token) : "";
                    return ask("region", t(ko,
                            "Where are you going — which country and city?"
                                    + (choices.isBlank() ? "" : " (allowed countries: " + choices + ")"),
                            "어디로 가시나요 — 어느 나라, 어느 도시인가요?"
                                    + (choices.isBlank() ? "" : " (허용 국가: " + choices + ")")));
                }
                String dest = destination;
                if (policy) {
                    String choices = destinationResolverAgentService.allowedCountryNames(true, token);
                    return ask("region", t(ko,
                            "One thing before we finish: this form only accepts registered regions, "
                                    + "and \"" + dest + "\" isn't on the list"
                                    + (choices.isBlank() ? "" : " (allowed countries: " + choices + ")")
                                    + ". Which allowed city are you going to?",
                            "마무리 전에 한 가지 — 이 양식은 등록된 지역만 선택할 수 있는데 \"" + dest
                                    + "\"이(가) 목록에 없어요"
                                    + (choices.isBlank() ? "" : " (허용 국가: " + choices + ")")
                                    + ". 허용된 지역 중 어느 도시로 가시나요?"));
                }
                // Non-policy form: any country/city is fine — the words just didn't resolve
                // to one. Ask plainly instead of implying a restriction that isn't there.
                return ask("region", t(ko,
                        "One thing before we finish: I couldn't match \"" + dest
                                + "\" to a country or city in the region list. "
                                + "Which country and city are you going to?",
                        "마무리 전에 한 가지 — \"" + dest + "\"을(를) 지역 목록의 국가/도시와 "
                                + "연결하지 못했어요. 어느 나라, 어느 도시로 가시나요?"));
            }
        }
        JsonNode routeItem = paperItem(paper, "BSTR_ROUTE");
        // ② 이동경로(출장) — the traveller's own route between the company's REGISTERED
        // destinations. Its legs carry the ids, addresses, admin codes and coordinates that the
        // save body's bstrRoutes[] needs, and only the traveller knows which sites they visit,
        // so it is asked (once) with that list in the question.
        // Answered either way: one route for the trip, or a route for every traveller
        // individually (the picker sends one per person).
        boolean everyoneHasOne = state.path("travelers").size() > 0;
        for (JsonNode n : state.path("travelers")) {
            if (!state.path("routePointsByTraveller").path(n.asText("")).isArray()) {
                everyoneHasOne = false;
                break;
            }
        }
        if (routeItem != null && routeItem.path("used").asBoolean(true)
                && state.path("routePoints").isMissingNode() && !everyoneHasOne
                && !state.path("routeAskSkipped").asBoolean(false)) {
            JsonNode options = routeOptions(token);
            if (!options.isEmpty()) {
                // Short on purpose: the picker below lists the registered destinations, so the
                // question does not repeat them. One line, and it says both ways of answering.
                int party = state.path("travelers").size();
                String perPerson = party > 1
                        ? t(ko, " Each traveller can have their own.", " 출장자별로 지정할 수 있어요.")
                        : "";
                return ask("route", t(ko,
                        "What was your travel route? Pick the departure, destination and return "
                                + "point below — or just tell me." + perPerson,
                        "이동경로가 어떻게 되나요? 아래에서 출발지·목적지·복귀지를 고르시거나 "
                                + "말씀해 주세요." + perPerson));
            }
        }
        // ③ ... and then how they travel it. Asked AFTER the route on purpose: the vehicle is a
        // property OF the journey, so a person answers it more easily once the journey is
        // named. A vehicle mentioned in ANY earlier message was already captured into state.
        if (routeItem != null && routeItem.path("used").asBoolean(true)
                && routeItem.path("itemDto").path("transportBound").path("transportSelectUsed").asBoolean(false)
                && state.path("transportType").asText("").isBlank()
                && !state.path("transportAskSkipped").asBoolean(false)) {
            return ask("transport", t(ko,
                    "And how will you mainly travel — flight, train, bus, or your own car? "
                            + "(each route leg needs a transport type)",
                    "그리고 이동은 주로 어떤 교통수단인가요? 비행기·기차·버스·자차 중에서 말씀해 "
                            + "주세요 (이동경로 구간마다 교통수단이 필요해요)"));
        }
        // ④ Optional 출장지 상세 (the period row's selectionMemo) — asked ONCE, nullable.
        // state carries "" after the ask (answered-or-skipped marker), so absence of the
        // key is the only state that triggers the question.
        if (("OVERSEA".equals(bstrType) || regionUsed) && !state.has("destinationDetail")) {
            return ask("detail", t(ko,
                    "One optional thing — any destination detail to note (building, floor, "
                            + "venue; up to 10 characters)? If there's none, just say so.",
                    "선택 사항 하나만요 — 출장지 상세(건물·층·장소, 10자 이내)가 있다면 "
                            + "알려주세요. 없으면 없다고 말씀해 주세요."));
        }
        return null;
    }


    @Override
    public void previewRoutes(ArrayNode documents, ObjectNode state, String token) {
        if (documents == null || documents.isEmpty() || !documents.get(0).isObject()) {
            return;
        }
        JsonNode paper = planPaper((ObjectNode) documents.get(0), token);
        JsonNode routeItem = paper == null ? null : paperItem(paper, "BSTR_ROUTE");
        if (routeItem == null || !routeItem.path("used").asBoolean(true)) {
            return;
        }
        String bstrType = paper.path("bstrType").asText("");
        boolean calcDistance = routeItem.path("itemDto").path("calcDistanceUsed").asBoolean(true);
        for (int i = 0; i < documents.size(); i++) {
            if (!documents.get(i).isObject()) {
                continue;
            }
            String who = state.path("travelers").path(i).asText("");
            buildRoutes((ObjectNode) documents.get(i), state, bstrType, calcDistance, token, who);
        }
    }

    @Override
    public JsonNode routeOptions(String token) {
        ArrayNode out = objectMapper.createArrayNode();
        try {
            for (JsonNode d : safeArray(bizplayGatewayService.getPlanDestinations(token))) {
                ObjectNode o = out.addObject();
                o.put("id", d.path("id").asLong());
                o.put("name", d.path("name").asText(""));
                JsonNode a = d.path("address");
                o.put("address", a.isObject()
                        ? firstText(a, "roadAddress", "jibunAddress") : a.asText(""));
                o.put("sido", a.isObject() ? a.path("sido").asText("") : d.path("sido").asText(""));
            }
        } catch (Exception e) {
            log.warn("Destination master unavailable: {}", e.getMessage());
        }
        return out;
    }

    @Override
    public JsonNode resolveRoutePoints(String message, List<String> travellerNames, String token,
                                       boolean korean) {
        if (message == null || message.isBlank()) {
            return null;
        }
        JsonNode options = routeOptions(token);
        if (options.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode o : options) {
            names.add(o.path("name").asText(""));
        }
        try {
            // The route is read from the user's own sentence against the company's own list —
            // no phrasing is predefined, and every name is validated against that list below.
            java.util.LinkedHashMap<String, String> wanted = new java.util.LinkedHashMap<>();
            wanted.put("route", "The registered travel destinations are: " + String.join(", ", names)
                    + ". If this message describes a TRAVEL ROUTE between them (where they "
                    + "set out from, where they went, where they returned to), list those "
                    + "places IN ORDER, '>'-separated, each name copied EXACTLY from that "
                    + "list — e.g. \"비즈플레이 > 티엑스알로보틱스(본사) > 비즈플레이\". Omit "
                    + "the field when the message describes no such route");
            if (travellerNames != null && travellerNames.size() > 1) {
                // Several people are on this trip and they may not travel together. Only when the
                // sentence pins the route on ONE of them does it become that person's own route.
                wanted.put("traveller", "The travellers on this trip are: "
                        + String.join(", ", travellerNames) + ". If the route above belongs to ONE "
                        + "of them in particular (\"김도하는 …\", \"for 김충북\"), answer with that "
                        + "person's name copied exactly. Omit the field when the route is simply "
                        + "the trip's route for everyone");
            }
            JsonNode got = slotFillerAgentService.extract(message, wanted,
                    korean, java.util.List.<String>of());
            String raw = got.path("route").asText("").trim();
            if (raw.isBlank()) {
                return null;
            }
            ArrayNode points = objectMapper.createArrayNode();
            for (String part : raw.split(">")) {
                String want = part.trim();
                if (want.isEmpty()) {
                    continue;
                }
                for (String cand : names) {
                    if (cand.equalsIgnoreCase(want) || cand.contains(want) || want.contains(cand)) {
                        points.add(cand);   // validated against the master — never invented
                        break;
                    }
                }
            }
            if (points.size() < 2) {
                return null;
            }
            // A trip goes AND comes back. "이동경로는 비즈플레이에서 티엑스알로보틱스(본사)" names where they set out
            // and where they went, not that they stayed there - and the ask itself promises the
            // return leg is added for them. So the loop is closed back to the departure unless
            // the user already named it; the reply prints the full path, so an unwanted return
            // leg is visible and can be corrected in words.
            if (!points.get(points.size() - 1).asText("")
                    .equals(points.get(0).asText(""))) {
                points.add(points.get(0).asText(""));
                log.info("[ENRICH] route closed back to the departure: {}", points);
            }
            ObjectNode out = objectMapper.createObjectNode();
            out.set("points", points);
            String who = got.path("traveller").asText("").trim();
            if (!who.isBlank() && travellerNames != null) {
                for (String cand : travellerNames) {
                    if (cand.equalsIgnoreCase(who) || cand.contains(who) || who.contains(cand)) {
                        // Validated twice: the name must be one of this trip's travellers AND
                        // actually written in the sentence. A name the model supplied from
                        // anywhere else turns a trip-wide route into one person's by accident.
                        if (message.contains(cand)) {
                            out.put("traveller", cand);
                        } else {
                            log.info("[ENRICH] route not pinned on {} — the message never names them.",
                                    cand);
                        }
                        break;
                    }
                }
            }
            log.info("[ENRICH] route read from the words: {}{}", points,
                    out.has("traveller") ? " (for " + out.path("traveller").asText() + ")" : "");
            return out;
        } catch (Exception e) {
            log.info("Route resolution failed: {}", e.getMessage());
            return null;
        }
    }

    /** The readiness ask as {kind, text} — the kind drives WHICH UI re-opens, the text is the words. */
    private ObjectNode ask(String kind, String text) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("kind", kind);
        out.put("text", text);
        return out;
    }






    // ---------------------------------------------------------------- paper --------------

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

    /** The paperItemOrderDto entry of one item type, honouring the BASIC_ITEM/ITEM split. */
    private JsonNode paperItem(JsonNode paper, String itemType) {
        for (JsonNode it : paper.path("paperItemOrderDto")) {
            String t = "BASIC_ITEM".equals(it.path("paperItemType").asText(""))
                    ? it.path("basicItemType").asText("")
                    : it.path("itemDto").path("itemType").asText("");
            if (itemType.equals(t)) {
                return it;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- region -------------






    /** The refusal message when a required region can't be resolved — names the allowed options. */
    private String regionAskMessage(String destination, String bstrType, boolean regionUsed,
                                    boolean policy, String token, boolean ko) {
        String choices = "";
        try {
            if ("OVERSEA".equals(bstrType)) {
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
                choices = String.join(", ", names);
            }
        } catch (Exception ignored) {
            // the message is still useful without the list
        }
        String dest = destination.isBlank() ? t(ko, "(none given)", "(미입력)") : destination;
        String kindEn = "OVERSEA".equals(bstrType) ? (regionUsed ? "city" : "country") : "province";
        String kindKo = "OVERSEA".equals(bstrType) ? (regionUsed ? "도시" : "국가") : "시도";
        if (policy) {
            return t(ko,
                    "This form requires the trip region, but \"" + dest + "\" doesn't match any "
                            + "allowed " + kindEn
                            + (choices.isBlank() ? "" : " (allowed countries: " + choices + ")")
                            + ". Please give a destination from the allowed list and try again.",
                    "이 양식은 출장 지역이 필수인데 \"" + dest + "\"이(가) 허용된 " + kindKo
                            + " 목록과 일치하지 않아요"
                            + (choices.isBlank() ? "" : " (허용 국가: " + choices + ")")
                            + ". 허용된 지역의 목적지를 알려주신 뒤 다시 제출해 주세요.");
        }
        // Non-policy paper: the full region master applies — there is no "allowed list",
        // the name simply didn't resolve. Don't imply a restriction that isn't there.
        return t(ko,
                "This form saves the trip region, but I couldn't match \"" + dest
                        + "\" to a " + kindEn + " in the region list. "
                        + "Please tell me the " + kindEn + " you're going to and try again.",
                "이 양식은 출장 지역을 저장하는데 \"" + dest + "\"을(를) 지역 목록의 " + kindKo
                        + "와(과) 연결하지 못했어요. 어느 " + kindKo
                        + "(으)로 가시는지 알려주신 뒤 다시 제출해 주세요.");
    }

    /** selectionId (+ display areaInfo) written into every BSTR_PERIOD selections row. */
    private int writeSelectionId(ObjectNode doc, long regionId, String label, String memo,
                                 java.util.Map<String, ObjectNode> dayOverrides) {
        int rows = 0;
        for (JsonNode issued : doc.withArray("issuedItems")) {
            if (!"BSTR_PERIOD".equals(issued.path("item").path("itemType").asText(""))) {
                continue;
            }
            for (JsonNode sel : issued.path("selections")) {
                // One selections row per trip day; selectionName leads with the row's date,
                // so a per-day assignment can override just its own row.
                String rowDate = sel.path("selectionName").asText("");
                rowDate = rowDate.length() >= 10 ? rowDate.substring(0, 10) : rowDate;
                ObjectNode ov = dayOverrides == null ? null : dayOverrides.get(rowDate);
                long id = ov != null ? ov.path("id").asLong() : regionId;
                String lb = ov != null ? ov.path("label").asText("") : label;
                String mm = ov != null && !ov.path("memo").asText("").isBlank()
                        ? ov.path("memo").asText("") : memo;
                ((ObjectNode) sel).put("selectionId", id);
                // The company screen sends the area name alongside the id — mirrored here so
                // the draft preview stays honest about which region each row means.
                ((ObjectNode) sel).put("selectionAreaInfo",
                        "{\"name\":\"" + lb.replace("\"", "\\\"") + "\"}");
                if (!mm.isBlank()) {
                    // 출장지 상세 — the guide caps it at 10 chars.
                    ((ObjectNode) sel).put("selectionMemo",
                            mm.length() > 10 ? mm.substring(0, 10) : mm);
                }
                if (ov != null) {
                    log.info("[ENRICH] period row {} overridden: {} (id {})", rowDate, lb, id);
                }
                rows++;
            }
        }
        return rows;
    }

    /** One BSTR_PERIOD selections row per trip day, as the provider's screen sends them —
     * selectionName/ErpCode both carry THAT day (times appended separately when the paper
     * stores time), so per-day destinations have a row each to land on. */
    private void rebuildPeriodRowsPerDay(ObjectNode doc) {
        String start = doc.path("bstrStartDate").asText("");
        String end = doc.path("bstrEndDate").asText("");
        if (!start.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return;
        }
        java.time.LocalDate from = java.time.LocalDate.parse(start);
        java.time.LocalDate to = end.matches("\\d{4}-\\d{2}-\\d{2}")
                ? java.time.LocalDate.parse(end) : from;
        if (to.isBefore(from)) {
            to = from;
        }
        for (JsonNode issued : doc.withArray("issuedItems")) {
            if (!"BSTR_PERIOD".equals(issued.path("item").path("itemType").asText(""))) {
                continue;
            }
            ArrayNode rows = objectMapper.createArrayNode();
            for (java.time.LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                ObjectNode row = rows.addObject();
                row.putNull("selectionId");
                row.put("selectionName", d.toString());
                row.put("selectionErpCode", d.toString());
                row.putNull("selectionMemo");
                row.putNull("selectionAreaInfo");
            }
            ((ObjectNode) issued).set("selections", rows);
            log.info("[ENRICH] BSTR_PERIOD normalized to {} per-day row(s) ({} ~ {})",
                    rows.size(), from, to);
        }
    }

    /** Bare yyyy-MM-dd period values get the screen's default times when the paper stores time. */
    private void appendPeriodTimes(ObjectNode doc) {
        for (JsonNode issued : doc.withArray("issuedItems")) {
            if (!"BSTR_PERIOD".equals(issued.path("item").path("itemType").asText(""))) {
                continue;
            }
            for (JsonNode sel : issued.path("selections")) {
                ObjectNode s = (ObjectNode) sel;
                String start = s.path("selectionName").asText("");
                String end = s.path("selectionErpCode").asText("");
                // The provider screen's captured save body separates date and time with "T"
                // ("2026-08-31T08:05") — mirrored exactly.
                if (start.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    s.put("selectionName", start + "T09:00");
                }
                if (end.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    s.put("selectionErpCode", end + "T18:00");
                }
            }
        }
    }

    // ---------------------------------------------------------------- routes -------------

    /**
     * A round trip origin → destination → origin, each point resolved against the company's
     * destination master first and TMap POI search second. Legs whose two ends both have
     * coordinates get a TMap driving distance; anything else stays distance 0 with no map —
     * exactly how the provider's screen degrades. Returns the number of legs written.
     */
    private int buildRoutes(ObjectNode doc, ObjectNode state, String bstrType,
                            boolean calcDistance, String token, String travellerName) {
        // The traveller's own route, when they gave one: every point is a registered destination,
        // so each leg carries real ids, addresses, admin codes and coordinates — the shape the
        // provider's Route Setup dialog produces. Legs run in the order named.
        // This traveller's OWN route wins over the trip-wide one — several people on one plan
        // do not always travel together, and each document carries its own legs.
        JsonNode chosen = state.path("routePoints");
        JsonNode own = travellerName == null || travellerName.isBlank()
                ? null : state.path("routePointsByTraveller").path(travellerName);
        if (own != null && own.isArray() && own.size() >= 2) {
            chosen = own;
            log.info("[ENRICH] using {}'s own route: {}", travellerName, own);
        }
        if (chosen.isArray() && chosen.size() >= 2) {
            List<ObjectNode> points = new ArrayList<>();
            for (JsonNode n : chosen) {
                ObjectNode p = resolvePoint(n.asText(""), token, true);
                if (p != null) {
                    points.add(p);
                }
            }
            if (points.size() >= 2) {
                String transportChosen = state.path("transportType").asText("");
                if (transportChosen.isBlank()) {
                    transportChosen = "OVERSEA".equals(bstrType) ? "PUBLIC_AIRLINE" : "PUBLIC";
                }
                ArrayNode legs = doc.putArray("bstrRoutes");
                for (int i = 0; i + 1 < points.size(); i++) {
                    legs.add(leg(i + 1, points.get(i), points.get(i + 1), transportChosen,
                            calcDistance, token));
                }
                log.info("[ENRICH] bstrRoutes written from the traveller's route: {} legs ({})",
                        legs.size(), chosen);
                return legs.size();
            }
        }
        String origin = state.path("origin").asText("").trim();
        String destination = state.path("destination").asText("").trim();
        if (destination.isBlank()) {
            return 0;
        }
        // The origin is the Korean side either way; the DESTINATION of an overseas trip must
        // never go through TMap POI search — it covers Korea only, so "오사카" resolves to a
        // Seoul restaurant of that name and the route leg points at the wrong continent. A
        // foreign destination keeps its name as the address, with no coordinates.
        ObjectNode from = origin.isBlank() ? null : resolvePoint(origin, token, true);
        ObjectNode to = resolvePoint(destination, token, !"OVERSEA".equals(bstrType));
        if (from == null) {
            // No origin named: anchor the trip at the company's first registered destination
            // (their HQ in practice) so a required route never stays empty for want of a word.
            from = firstRegisteredDestination(token);
        }
        if (from == null || to == null) {
            return 0;
        }
        // The transport the user named in chat wins (captured into state as a provider enum);
        // otherwise foreign trips fly and domestic ones default to public transport — the same
        // form-level default the screen proposes.
        String transport = state.path("transportType").asText("");
        if (transport.isBlank()) {
            transport = "OVERSEA".equals(bstrType) ? "PUBLIC_AIRLINE" : "PUBLIC";
        }
        ArrayNode routes = doc.putArray("bstrRoutes");
        routes.add(leg(1, from, to, transport, calcDistance, token));
        routes.add(leg(2, to, from, transport, calcDistance, token));
        log.info("[ENRICH] bstrRoutes written: {} legs ({} ↔ {})", routes.size(),
                from.path("address").asText(""), to.path("address").asText(""));
        return routes.size();
    }

    /** One bstrRoutes leg between two resolved points (see the guide §2.4). */
    private ObjectNode leg(int order, ObjectNode from, ObjectNode to, String transport,
                           boolean calcDistance, String token) {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("routeOrder", order);
        r.put("fare", 0);
        if (from.hasNonNull("id")) {
            r.put("departureId", from.path("id").asLong());
        }
        r.put("departureAddress", from.path("address").asText(""));
        r.put("departureSido", from.path("sido").asText(""));
        r.put("departureAdminCode", from.path("adminCode").asText(""));
        if (to.hasNonNull("id")) {
            r.put("arrivalId", to.path("id").asLong());
        }
        r.put("arrivalAddress", to.path("address").asText(""));
        r.put("arrivalSido", to.path("sido").asText(""));
        r.put("arrivalAdminCode", to.path("adminCode").asText(""));
        r.put("transportType", transport);
        double distance = 0.0;
        boolean coords = !from.path("lon").asText("").isBlank() && !to.path("lon").asText("").isBlank();
        if (coords) {
            ObjectNode map = r.putObject("mapInfo");
            map.put("startX", from.path("lon").asText(""));
            map.put("startY", from.path("lat").asText(""));
            map.put("endX", to.path("lon").asText(""));
            map.put("endY", to.path("lat").asText(""));
            if (calcDistance) {
                distance = tmapDistanceKm(from, to, token);
            }
        }
        r.put("distance", distance);
        return r;
    }

    /**
     * One place as {id?, address, sido, adminCode, lat, lon}. The company's registered
     * destinations win (they carry the destination id the provider links back to); anything
     * else goes through the TMap POI bypass. Foreign places typically resolve to nothing —
     * then the point keeps its name as the address and no coordinates, which the save accepts.
     */
    private ObjectNode resolvePoint(String name, String token, boolean allowPoi) {
        ObjectNode p = objectMapper.createObjectNode();
        try {
            log.info("[ENRICH] GET bstr/destination/popUp/active/list — resolving point '{}'", name);
            JsonNode dests = bizplayGatewayService.getPlanDestinations(token);
            for (JsonNode d : safeArray(dests)) {
                String dn = d.path("name").asText("");
                if (!dn.isBlank() && (dn.contains(name) || name.contains(dn))) {
                    JsonNode a = d.path("address");
                    p.put("id", d.path("id").asLong());
                    // Two shapes of the same master: destination/active/list is FLAT (address is
                    // a string, 시도/adminCode/coordinates sit on the row), the popUp variant
                    // nests them under address. Read whichever this list carries.
                    p.put("address", a.isObject()
                            ? firstText(a, "roadAddress", "jibunAddress") : a.asText(""));
                    p.put("sido", a.isObject() ? a.path("sido").asText("") : d.path("sido").asText(""));
                    p.put("adminCode", a.isObject()
                            ? a.path("adminCode").asText("") : d.path("adminCode").asText(""));
                    p.put("lat", firstText(d, "latitude").isBlank()
                            ? a.path("latitude").asText("") : firstText(d, "latitude"));
                    p.put("lon", firstText(d, "longitude").isBlank()
                            ? a.path("longitude").asText("") : firstText(d, "longitude"));
                    return p;
                }
            }
        } catch (Exception e) {
            log.warn("Destination master unavailable while resolving '{}': {}", name, e.getMessage());
        }
        if (allowPoi) {
        try {
            ObjectNode env = objectMapper.createObjectNode();
            env.put("method", "GET");
            env.put("url", "https://api2.sktelecom.com/tmap/pois");
            // Korean search words must stay UNencoded through the bypass (guide §2.2).
            env.put("uri", "?version=1&format=json&searchKeyword=" + name
                    + "&page=1&count=1&searchtypCd=A&buildingNameYn=N");
            env.putObject("requestParam");
            log.info("[ENRICH] POST misc/bypass (TMap POI) — geocoding '{}'", name);
            JsonNode res = bizplayGatewayService.postBypass(env, token);
            JsonNode poi = res.path("searchPoiInfo").path("pois").path("poi").path(0);
            if (poi.isObject()) {
                String road = poi.path("newAddressList").path("newAddress").path(0)
                        .path("fullAddressRoad").asText("");
                if (road.isBlank()) {
                    road = (poi.path("upperAddrName").asText("") + " "
                            + poi.path("middleAddrName").asText("") + " "
                            + poi.path("lowerAddrName").asText("") + " "
                            + poi.path("detailAddrName").asText("")).trim();
                }
                p.put("address", road);
                p.put("sido", poi.path("upperAddrName").asText(""));
                p.put("adminCode", poi.path("adminDongCode").asText(""));
                p.put("lat", poi.path("frontLat").asText(""));
                p.put("lon", poi.path("frontLon").asText(""));
                return p;
            }
        } catch (Exception e) {
            log.info("TMap POI lookup failed for '{}': {}", name, e.getMessage());
        }
        }
        // Unresolvable (a foreign city, a nickname): keep the words as the address so the leg
        // still says where it goes; no coordinates, so no distance and no map.
        p.put("address", name);
        p.put("sido", "");
        p.put("adminCode", "");
        p.put("lat", "");
        p.put("lon", "");
        return p;
    }

    private ObjectNode firstRegisteredDestination(String token) {
        try {
            JsonNode dests = bizplayGatewayService.getPlanDestinations(token);
            JsonNode d = safeArray(dests).size() > 0 ? dests.get(0) : null;
            if (d != null) {
                return resolvePoint(d.path("name").asText(""), token, true);
            }
        } catch (Exception e) {
            log.info("No registered destination to anchor the route: {}", e.getMessage());
        }
        return null;
    }

    /** TMap driving distance in km (1 decimal), 0 on any failure — the screen degrades the same. */
    private double tmapDistanceKm(ObjectNode from, ObjectNode to, String token) {
        try {
            ObjectNode env = objectMapper.createObjectNode();
            env.put("method", "POST");
            env.put("url", "https://api2.sktelecom.com");
            env.put("uri", "/tmap/routes");
            ObjectNode q = env.putObject("requestParam");
            q.put("startX", from.path("lon").asText(""));
            q.put("startY", from.path("lat").asText(""));
            q.put("endX", to.path("lon").asText(""));
            q.put("endY", to.path("lat").asText(""));
            q.put("searchOption", 10);
            q.put("carType", 0);
            q.put("sort", "index");
            q.put("trafficInfo", "N");
            q.put("mainRoadInfo", "N");
            q.put("tollgateFareInfo", "N");
            log.info("[ENRICH] POST misc/bypass (TMap routes) — distance {} -> {}",
                    from.path("address").asText(""), to.path("address").asText(""));
            JsonNode res = bizplayGatewayService.postBypass(env, token);
            long meters = res.path("features").path(0).path("properties")
                    .path("totalDistance").asLong(0);
            return Math.round(meters / 1000.0 * 10.0) / 10.0;
        } catch (Exception e) {
            log.info("TMap distance lookup failed: {}", e.getMessage());
            return 0.0;
        }
    }

    // ---------------------------------------------------------------- misc ---------------

    private ArrayNode safeArray(JsonNode n) {
        return n != null && n.isArray() ? (ArrayNode) n : objectMapper.createArrayNode();
    }

    private String firstText(JsonNode n, String... keys) {
        for (String k : keys) {
            String v = n.path(k).asText("");
            if (!v.isBlank() && !"null".equals(v)) {
                return v;
            }
        }
        return "";
    }

    private static String t(boolean ko, String en, String kr) {
        return ko ? kr : en;
    }
}
