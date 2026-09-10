package com.api.bizplay_conversational.service.ruledAmountLookupService;

import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * See {@link RuledAmountLookupService}. Every rule below cites the provider's document it comes
 * from ({@code src/main/resources/증빙 규정금액 산출/}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuledAmountLookupServiceImple implements RuledAmountLookupService {

    /** 01 §2.7: the provider's client refuses to call with anything but this shape. */
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /** 01 §2.3.2 rows 2-4: yes/no 출장항목 whose ITEM id goes into exceptionRuleInputItemIds. */
    private static final Set<String> YESNO_APPLY_ITEM_TYPES =
            Set.of("EXPENSE_BEYOND_BSTR_PERIOD", "HOMETOWN", "REQUEST_STAFF_LODGE");

    private final BizplayGatewayService bizplayGatewayService;
    private final ObjectMapper objectMapper;

    /** One 급지 section of the trip: the region and the dates it covers (04 §4.5, §4.10). */
    private record Section(Long regionId, LocalDate start, LocalDate end) { }

    @Override
    public ObjectNode lookup(JsonNode doc, JsonNode receipt, JsonNode planPaper, long corporationId,
                             List<Long> corpUserIds, String token) {
        return lookup(doc, receipt, planPaper, corporationId, corpUserIds, token, null);
    }

    @Override
    public ObjectNode lookup(JsonNode doc, JsonNode receipt, JsonNode planPaper, long corporationId,
                             List<Long> corpUserIds, String token, KrwRate rates) {
        String tranKindType = receipt.path("tranKindType").asText("");
        long tranKindId = receipt.path("tranKindId").asLong(0);
        if (tranKindType.isBlank() || corpUserIds == null || corpUserIds.isEmpty()) {
            // 01 §2.7: an empty tranKindType is a 400 from the provider; nothing to ask.
            return null;
        }
        LocalDate tripStart = date(doc.path("bstrStartDate").asText(""));
        LocalDate tripEnd = date(doc.path("bstrEndDate").asText(""));
        if (tripStart == null) {
            log.info("[POLICY] no trip start date on the document - 규정 not looked up");
            return null;
        }
        if (tripEnd == null || tripEnd.isBefore(tripStart)) {
            tripEnd = tripStart;
        }

        // 04 §4.5 / §4.10: the sections come from the plan's 출장기간 selections - selectionId is
        // the region, selectionName the section start, selectionErpCode its end. A trip with no
        // sections is one section over the whole period with no region.
        List<Section> sections = sections(doc, tripStart, tripEnd);
        int[] allow = allowDays(doc, planPaper, tranKindId);
        List<Section> widened = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            LocalDate start = i == 0 ? s.start().minusDays(allow[0]) : s.start();
            LocalDate end = i == sections.size() - 1 ? s.end().plusDays(allow[1]) : s.end();
            widened.add(new Section(s.regionId(), start, end));
        }

        ArrayNode exceptionIds = exceptionRuleInputItemIds(doc);
        String activityDivision = activityDivision(doc);
        ObjectNode calcInputs = calcInputs(doc);
        JsonNode etc = receipt.path("receiptEtc");
        CalcConditionEngine.TravelDayOpts travelDays = travelDayOpts(doc, planPaper);

        // corpUserId -> per-day FINAL amounts (layer ② applied per section), merged over sections
        // by MAX (04 §4.10; 설명 §7-4: a per-diem is paid once a day, so overlapping sections never
        // add up). Three maps ride along: the 규정's own currency, KRW, and the untouched base.
        Map<Long, TreeMap<String, Double>> perUser = new LinkedHashMap<>();
        Map<Long, TreeMap<String, Double>> perUserKrw = new LinkedHashMap<>();
        Map<Long, TreeMap<String, Double>> perUserBase = new LinkedHashMap<>();
        boolean krwAvailable = true;
        boolean conditionsApplied = false;
        ObjectNode breakdown = null;
        TreeMap<String, String> dayTypes = new TreeMap<>();
        ArrayNode sectionsOut = objectMapper.createArrayNode();
        JsonNode first = null;
        int calls = 0;
        for (Long corpUserId : corpUserIds) {
            TreeMap<String, Double> mine = perUser.computeIfAbsent(corpUserId, k -> new TreeMap<>());
            TreeMap<String, Double> mineKrw = perUserKrw.computeIfAbsent(corpUserId, k -> new TreeMap<>());
            TreeMap<String, Double> mineBase = perUserBase.computeIfAbsent(corpUserId, k -> new TreeMap<>());
            for (Section s : widened) {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("bstrDate", s.start().toString());
                body.put("bstrEndDate", s.end().toString());
                body.put("corporationId", corporationId);
                body.put("corporationUserId", corpUserId);
                // 01 §2.2: nullable keys must be PRESENT - the provider's client always sends them.
                putTextOrNull(body, "vehicleType", "FOOD".equals(tranKindType) ? "" : etc.path("vehicleType").asText(""));
                body.put("tranKindType", tranKindType);
                if (tranKindId > 0) {
                    body.put("tranKindId", tranKindId);
                }
                putLongOrNull(body, "bstrPurposeId", doc.path("bstrPurposeId"));
                putLongOrNull(body, "bstrSegmentId", doc.path("bstrSegmentId"));
                putLongOrNull(body, "bstrDepartureId", firstRouteId(doc, "departureId"));
                putLongOrNull(body, "bstrDestinationId", firstRouteId(doc, "arrivalId"));
                body.putNull("bstrAreaCode");
                if (s.regionId() == null) {
                    body.putNull("bstrRegionId");
                } else {
                    body.put("bstrRegionId", s.regionId());
                }
                String bstrType = doc.path("bstrType").asText("");
                if (!bstrType.isBlank()) {
                    body.put("bstrType", bstrType);      // "OVERSEA" (no S) / "DOMESTIC" - 설명 §7-12
                }
                if (!exceptionIds.isEmpty()) {
                    body.set("exceptionRuleInputItemIds", exceptionIds.deepCopy());
                }
                if (activityDivision != null) {
                    body.put("activityDivision", activityDivision);   // 01 §2.3.5, only from the form item
                }
                String food = etc.path("foodDivisionType").asText("");
                if ("FOOD".equals(tranKindType) && !food.isBlank()) {
                    body.put("foodDivisionType", food);              // 04 §4.6: without it the row is unpredictable
                }
                if (etc.hasNonNull("id")) {
                    body.put("receiptEtcId", etc.path("id").asLong());
                }
                // Always in 조건식 모드 (01 §2.1): the conditions only come back when calcInputs is
                // sent, and an empty object is a valid input set.
                body.set("calcInputs", calcInputs.deepCopy());

                JsonNode res;
                try {
                    res = bizplayGatewayService.getRenewalLimit(body, token);
                } catch (RuntimeException e) {
                    log.warn("[POLICY] renewal/limit failed for user {} section {}: {}", corpUserId,
                            s.regionId(), e.getMessage());
                    continue;                                          // 04 §4.6: partial success
                }
                calls++;
                if (res == null || !res.isObject()) {
                    continue;                                          // no 규정 for this section
                }
                if (first == null) {
                    first = res;
                }
                ObjectNode secOut = sectionsOut.addObject();
                if (s.regionId() == null) {
                    secOut.putNull("regionId");
                } else {
                    secOut.put("regionId", s.regionId());
                }
                secOut.put("start", s.start().toString());
                secOut.put("end", s.end().toString());
                secOut.put("corpUserId", corpUserId);
                secOut.set("response", res.deepCopy());
                // The base: the per-day map, or the scalar spread over the section's days.
                TreeMap<String, Double> raw = new TreeMap<>();
                JsonNode amounts = res.path("limitAmounts");
                if (amounts.isObject() && amounts.size() > 0) {
                    amounts.fields().forEachRemaining(e -> raw.put(e.getKey(), e.getValue().asDouble(0)));
                } else if (res.path("limitAmount").isNumber()) {
                    for (LocalDate d = s.start(); !d.isAfter(s.end()); d = d.plusDays(1)) {
                        raw.put(d.toString(), res.path("limitAmount").asDouble(0));
                    }
                }
                raw.forEach((day, amt) -> mineBase.merge(day, amt, Math::max));

                // Layer ② on the absolute axis of THIS request (02 §3-5 (A): the response never
                // carries calcPeriod; the request's dates are it). The engine is currency-blind
                // (02 §6-3): a KRW 규정 runs as it is, a foreign 규정 is converted to KRW FIRST -
                // the tenant's USD 100/day room rule carries a '+10,000' condition, and 100 + 10000
                // on a USD base is nonsense - and the foreign figure is the KRW result divided back.
                String policyCurrency = res.path("currencyCode").asText("").trim().toUpperCase();
                boolean foreignPolicy = CalcConditionEngine.isForeign(policyCurrency);
                Set<String> needed = new LinkedHashSet<>(CalcConditionEngine.operandCurrencies(res));
                if (foreignPolicy) {
                    needed.add(policyCurrency);
                }
                Map<String, Double> perUnit = new LinkedHashMap<>();
                if (!needed.isEmpty()) {
                    if (rates == null) {
                        perUnit = null;
                        log.info("[POLICY] section {} needs rates for {} - none supplied", s.regionId(), needed);
                    } else {
                        for (String cur : needed) {
                            double rate = rates.perUnit(cur);
                            if (Double.isNaN(rate) || rate <= 0) {
                                log.info("[POLICY] no KRW rate for {} - the KRW 규정금액 cannot be built", cur);
                                perUnit = null;
                                break;
                            }
                            perUnit.put(cur, rate);
                        }
                    }
                }
                if (perUnit == null) {
                    // No KRW figure: the base stands untouched and the conditions are deferred.
                    krwAvailable = false;
                    raw.forEach((day, amt) -> mine.merge(day, amt, Math::max));
                } else {
                    double policyRate = foreignPolicy ? perUnit.get(policyCurrency) : 1.0;
                    TreeMap<String, Double> krwBase = new TreeMap<>();
                    raw.forEach((day, amt) -> krwBase.put(day, amt * policyRate));
                    JsonNode converted = needed.isEmpty() ? res : CalcConditionEngine.withOperandsInKrw(res, perUnit);
                    CalcConditionEngine.Result krwRun = CalcConditionEngine.apply(
                            converted, krwBase, travelDays, s.start().toString(), s.end().toString());
                    krwRun.limitAmounts().forEach((day, amt) -> mineKrw.merge(day, amt, Math::max));
                    krwRun.limitAmounts().forEach((day, amt) -> mine.merge(day, amt / policyRate, Math::max));
                    if (krwRun.calcBreakdown() != null && krwRun.calcBreakdown().path("items").size() > 0) {
                        conditionsApplied = true;
                    }
                    if (breakdown == null && krwRun.calcBreakdown() != null) {
                        breakdown = krwRun.calcBreakdown();
                    }
                }
                JsonNode types = res.path("dayTypeMap");
                if (types.isObject()) {
                    types.fields().forEachRemaining(e -> dayTypes.putIfAbsent(e.getKey(), e.getValue().asText("")));
                }
            }
        }
        if (first == null) {
            log.info("[POLICY] no 규정 for {} (tranKind {}) after {} call(s)", tranKindType, tranKindId, calls);
            return null;
        }

        // Users merge by SUM per day (04 §4.6: each companion has their own limit; meals only -
        // every other 용도 was asked for the drafter alone).
        TreeMap<String, Double> merged = new TreeMap<>();
        for (TreeMap<String, Double> mine : perUser.values()) {
            mine.forEach((day, amt) -> merged.merge(day, amt, Double::sum));
        }
        TreeMap<String, Double> mergedKrw = new TreeMap<>();
        for (TreeMap<String, Double> mine : perUserKrw.values()) {
            mine.forEach((day, amt) -> mergedKrw.merge(day, amt, Double::sum));
        }
        TreeMap<String, Double> mergedBase = new TreeMap<>();
        for (TreeMap<String, Double> mine : perUserBase.values()) {
            mine.forEach((day, amt) -> mergedBase.merge(day, amt, Double::sum));
        }
        // The scalar limitAmount: each user's first answer, summed over users (04 §4.6 sums the
        // scalar for meals; for one user it is simply the first answer).
        double scalar = 0;
        for (Long corpUserId : perUser.keySet()) {
            for (JsonNode sec : sectionsOut) {
                if (sec.path("corpUserId").asLong() == corpUserId) {
                    scalar += sec.path("response").path("limitAmount").asDouble(0);
                    break;
                }
            }
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.set("id", first.path("id").deepCopy());
        out.put("limitAmount", scalar);
        ObjectNode amounts = out.putObject("limitAmounts");
        merged.forEach(amounts::put);
        ObjectNode types = out.putObject("dayTypeMap");
        dayTypes.forEach(types::put);
        for (String k : new String[]{"currencyCode", "bstrPayClassType", "bstrPayOptionType",
                "bstrCategoryType", "payType", "bstrTransportGradeType", "tranKindType", "calcEnabled",
                "calcPeriod", "exceptionReasonUsed", "exceptionReasonRequired"}) {
            if (first.has(k)) {
                out.set(k, first.get(k).deepCopy());
            }
        }
        out.set("appliedConditions", first.path("appliedConditions").isArray()
                ? first.get("appliedConditions").deepCopy() : objectMapper.createArrayNode());
        out.set("sections", sectionsOut);
        out.put("allowDaysBefore", allow[0]);
        out.put("allowDaysAfter", allow[1]);
        ObjectNode baseOut = out.putObject("baseLimitAmounts");
        mergedBase.forEach(baseOut::put);
        if (krwAvailable) {
            ObjectNode krwOut = out.putObject("limitAmountsKrw");
            mergedKrw.forEach(krwOut::put);
        }
        out.put("calcApplied", conditionsApplied);
        out.put("calcDeferred", !krwAvailable && out.path("appliedConditions").size() > 0);
        if (breakdown != null) {
            out.set("calcBreakdown", breakdown);
        }
        out.put("travelDaysPre", travelDays.preCount());
        out.put("travelDaysPost", travelDays.postCount());
        log.info("[POLICY] {} (tranKind {}): {} call(s) over {} section(s) x {} user(s) -> {} day(s), "
                        + "{} {} limit={} {}, {} condition(s) matched, layer ② {} - {} {} / KRW {}",
                tranKindType, tranKindId, calls, widened.size(), corpUserIds.size(), merged.size(),
                out.path("bstrPayClassType").asText("?"), out.path("currencyCode").asText("?"),
                scalar, out.path("payType").asText(""), out.path("appliedConditions").size(),
                conditionsApplied ? "applied: " + (breakdown == null ? "" : breakdown.path("items").toString())
                        : "nothing to apply",
                out.path("currencyCode").asText("?"), merged, krwAvailable ? mergedKrw : "unavailable");
        return out;
    }

    /**
     * 02 §5-1 detectTravelDays: the plan's checked EXPENSE_BEYOND_BSTR_PERIOD items whose ERP code
     * is PREDEPART (전일출발) or NEXTARRIVE (익일도착) - each makes one travel day, and its item id,
     * as a STRING, names the conditions that belong to that day. The document's slim item echo
     * may lack the code; the plan form definition carries it.
     */
    private CalcConditionEngine.TravelDayOpts travelDayOpts(JsonNode doc, JsonNode planPaper) {
        int pre = 0;
        int post = 0;
        String preType = null;
        String postType = null;
        for (JsonNode row : doc.path("issuedItems")) {
            JsonNode item = row.path("item");
            if (!"EXPENSE_BEYOND_BSTR_PERIOD".equals(item.path("itemType").asText(""))
                    || !"true".equals(row.path("value").asText(""))) {
                continue;
            }
            long itemId = item.path("id").asLong(0);
            String erp = item.path("erpCode").asText("");
            if (erp.isBlank() && planPaper != null) {
                JsonNode def = paperItem(planPaper, itemId);
                erp = def == null ? "" : def.path("erpCode").asText("");
            }
            if ("PREDEPART".equals(erp)) {
                pre = 1;
                preType = String.valueOf(itemId);
            } else if ("NEXTARRIVE".equals(erp)) {
                post = 1;
                postType = String.valueOf(itemId);
            }
        }
        if (pre > 0 || post > 0) {
            log.info("[POLICY] travel days: pre={} ({}) post={} ({})", pre, preType, post, postType);
        }
        return new CalcConditionEngine.TravelDayOpts(pre, post, preType, postType);
    }

    // --- pieces ----------------------------------------------------------------------------

    /**
     * The 급지 sections of the trip, read off the settlement document's 출장기간 item exactly as
     * the provider's client reads the plan (04 §4.5 "급지 구간의 날짜 필드"): selectionId = region,
     * selectionName = start, selectionErpCode = end. Selections without dates are skipped; none at
     * all means one section over the trip with no region.
     */
    private List<Section> sections(JsonNode doc, LocalDate tripStart, LocalDate tripEnd) {
        List<Section> out = new ArrayList<>();
        for (JsonNode row : doc.path("issuedItems")) {
            if (!"BSTR_PERIOD".equals(row.path("item").path("itemType").asText(""))) {
                continue;
            }
            for (JsonNode sel : row.path("selections")) {
                LocalDate start = date(sel.path("selectionName").asText(""));
                LocalDate end = date(sel.path("selectionErpCode").asText(""));
                if (start == null && end == null) {
                    continue;
                }
                if (start == null) {
                    start = end;
                }
                if (end == null || end.isBefore(start)) {
                    end = start;
                }
                Long region = sel.hasNonNull("selectionId") && sel.path("selectionId").asLong(0) > 0
                        ? sel.path("selectionId").asLong() : null;
                out.add(new Section(region, start, end));
            }
        }
        if (out.isEmpty()) {
            out.add(new Section(null, tripStart, tripEnd));
        }
        return out;
    }

    /**
     * 04 §4.5 "허용일수": the plan's checked EXPENSE_BEYOND_BSTR_PERIOD items, each with the form's
     * periodCondition.tranKindConditionDtos - take the rows for THIS 용도 and the maximum of their
     * pre / post days. {0, 0} when nothing is checked or the form is unknown.
     */
    private int[] allowDays(JsonNode doc, JsonNode planPaper, long tranKindId) {
        int pre = 0;
        int post = 0;
        if (planPaper == null || tranKindId <= 0) {
            return new int[]{0, 0};
        }
        for (JsonNode row : doc.path("issuedItems")) {
            JsonNode item = row.path("item");
            if (!"EXPENSE_BEYOND_BSTR_PERIOD".equals(item.path("itemType").asText(""))
                    || !"true".equals(row.path("value").asText(""))) {
                continue;
            }
            long itemId = item.path("id").asLong(0);
            JsonNode def = paperItem(planPaper, itemId);
            if (def == null || !def.path("activated").asBoolean(true)) {
                continue;
            }
            for (JsonNode cond : def.path("periodCondition").path("tranKindConditionDtos")) {
                if (cond.path("tranKindId").asLong(0) == tranKindId) {
                    pre = Math.max(pre, cond.path("preBstrAllowanceDays").asInt(0));
                    post = Math.max(post, cond.path("postBstrAllowanceDays").asInt(0));
                }
            }
        }
        return new int[]{pre, post};
    }

    private JsonNode paperItem(JsonNode paper, long itemId) {
        for (JsonNode entry : paper.path("paperItemOrderDto")) {
            JsonNode item = entry.path("itemDto");
            if (item.path("id").asLong(0) == itemId) {
                return item;
            }
        }
        return null;
    }

    /**
     * 01 §2.3.2: BSTR_SELECT selections contribute their selectionId; the yes/no 출장항목
     * (EXPENSE_BEYOND_BSTR_PERIOD, HOMETOWN, REQUEST_STAFF_LODGE) contribute the ITEM id when
     * checked. One array, de-duplicated.
     */
    private ArrayNode exceptionRuleInputItemIds(JsonNode doc) {
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode row : doc.path("issuedItems")) {
            JsonNode item = row.path("item");
            String type = item.path("itemType").asText("");
            if ("BSTR_SELECT".equals(type)) {
                for (JsonNode sel : row.path("selections")) {
                    if (sel.hasNonNull("selectionId") && sel.path("selectionId").asLong(0) > 0) {
                        ids.add(sel.path("selectionId").asLong());
                    }
                }
            } else if (YESNO_APPLY_ITEM_TYPES.contains(type) && "true".equals(row.path("value").asText(""))
                    && item.path("id").asLong(0) > 0) {
                ids.add(item.path("id").asLong());
            }
        }
        ArrayNode out = objectMapper.createArrayNode();
        ids.forEach(out::add);
        return out;
    }

    /** 01 §2.3.5: the ACTIVITY_EXPENSE_TYPE item's value, ACTUAL or FIXED, else nothing is sent. */
    private String activityDivision(JsonNode doc) {
        for (JsonNode row : doc.path("issuedItems")) {
            if ("ACTIVITY_EXPENSE_TYPE".equals(row.path("item").path("itemType").asText(""))) {
                String v = row.path("value").asText("");
                if ("ACTUAL".equals(v) || "FIXED".equals(v)) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * 01 §2.3: what the document itself can answer - the route's destination ids and how many
     * places were visited. Times come only when the plan's period selections carry them. Fields
     * the provider's client never fills (isLodging, isMealTwiceOrMore, department, company) are
     * left out, as theirs are.
     */
    private ObjectNode calcInputs(JsonNode doc) {
        ObjectNode in = objectMapper.createObjectNode();
        Set<Long> destinationIds = new LinkedHashSet<>();
        List<Long> arrivals = new ArrayList<>();
        JsonNode routes = doc.path("bstrRoutes");
        for (JsonNode r : routes) {
            if (r.path("departureId").asLong(0) > 0) {
                destinationIds.add(r.path("departureId").asLong());
            }
            if (r.path("arrivalId").asLong(0) > 0) {
                destinationIds.add(r.path("arrivalId").asLong());
                arrivals.add(r.path("arrivalId").asLong());
            }
        }
        if (!destinationIds.isEmpty()) {
            ArrayNode ids = in.putArray("destinationIds");
            destinationIds.forEach(ids::add);
            // Visited places: the arrivals minus the final return point (the last leg comes home).
            int visited = Math.max(0, new LinkedHashSet<>(arrivals.size() > 1
                    ? arrivals.subList(0, arrivals.size() - 1) : arrivals).size());
            in.put("visitedDestinationCount", visited);
        }
        String dep = null;
        String arr = null;
        for (JsonNode row : doc.path("issuedItems")) {
            if (!"BSTR_PERIOD".equals(row.path("item").path("itemType").asText(""))) {
                continue;
            }
            JsonNode sels = row.path("selections");
            if (sels.size() > 0) {
                dep = time(sels.get(0).path("selectionMemo").asText(""));
                arr = time(sels.get(sels.size() - 1).path("selectionMemo").asText(""));
            }
        }
        if (dep != null) {
            in.put("departureTime", dep);
        }
        if (arr != null) {
            in.put("arrivalTime", arr);
        }
        return in;
    }

    private Long firstRouteId(JsonNode doc, String field) {
        for (JsonNode r : doc.path("bstrRoutes")) {
            if (r.path(field).asLong(0) > 0) {
                return r.path(field).asLong();
            }
        }
        return null;
    }

    private static LocalDate date(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.length() >= 10) {
            t = t.substring(0, 10);
        }
        if (!ISO_DATE.matcher(t).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(t);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "HH:mm" out of a memo like "09:00" or "09:00:00"; null when the memo is not a time. */
    private static String time(String memo) {
        if (memo == null) {
            return null;
        }
        String t = memo.trim();
        return t.matches("^\\d{2}:\\d{2}(:\\d{2})?$") ? t.substring(0, 5) : null;
    }

    private static void putTextOrNull(ObjectNode body, String key, String value) {
        if (value == null || value.isBlank()) {
            body.putNull(key);
        } else {
            body.put(key, value);
        }
    }

    private static void putLongOrNull(ObjectNode body, String key, JsonNode value) {
        if (value != null && value.isNumber() && value.asLong(0) > 0) {
            body.put(key, value.asLong());
        } else {
            body.putNull(key);
        }
    }

    private static void putLongOrNull(ObjectNode body, String key, Long value) {
        if (value == null) {
            body.putNull(key);
        } else {
            body.put(key, value.longValue());
        }
    }
}
