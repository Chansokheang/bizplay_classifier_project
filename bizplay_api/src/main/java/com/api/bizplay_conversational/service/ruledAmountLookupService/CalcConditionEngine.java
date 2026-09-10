package com.api.bizplay_conversational.service.ruledAmountLookupService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 규정금액 layer ② - the condition engine (증빙 규정금액 산출/02_조건식적용_규칙). A port of BizPlay's
 * {@code calc-condition-engine.ts}: the renewal lookup answers BASE amounts per day plus the
 * matched {@code appliedConditions[]}; the caller lays the conditions over the base, dates
 * outside and conditions inside, accumulating within a day only, in ascending {@code sortOrder}.
 * <p>
 * The engine is pure and currency-blind (02 §6): hand it a KRW base map when the 규정 or a
 * condition operand is foreign, after converting them - never the other way round. It does not
 * round (02 §1-0 rule 1); truncation belongs to the receipt stage. Verified against the 71
 * golden vectors in {@code 규정금액_골든벡터.json} (see {@code CalcConditionEngineGoldenTest}).
 */
public final class CalcConditionEngine {

    private CalcConditionEngine() {
    }

    /** 02 §5-1: the travel-day options - counts are 0 or 1, the types are item ids AS STRINGS. */
    public record TravelDayOpts(int preCount, int postCount, String predepartType, String nextarriveType) {
        public static final TravelDayOpts NONE = new TravelDayOpts(0, 0, null, null);
    }

    /** The per-day final amounts and the calculation detail (null when the guards passed through). */
    public record Result(TreeMap<String, Double> limitAmounts, ObjectNode calcBreakdown) {
        public double total() {
            double sum = 0;
            for (double v : limitAmounts.values()) {
                sum += v;
            }
            return sum;
        }
    }

    /** 02 §3-5: where a date sits on the trip - travel-day slot or core, and its core position. */
    private record DayPosition(String slot, boolean isFirst, boolean isLast, long dayFromEnd, long coreTotalDays) { }

    private record Positions(Map<String, DayPosition> byDate, boolean travelDayAssignable) { }

    private static final Pattern YMD = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)");
    private static final Set<String> PERCENT_UNITS = Set.of("PERCENT", "%");
    private static final Set<String> DAY_TYPE_ITEM = Set.of("DAY_TYPE", "dayType");
    private static final String DAY_TYPE_ALWAYS = "평일/공휴일";
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");

    /**
     * Apply the response's conditions to {@code baseMap} (the response's own {@code limitAmounts}
     * when null) with the absolute period axis {@code periodStart..periodEnd} (02 §3-5 (A); the
     * request's trip dates - the response never carries them). Returns the base untouched, with
     * no breakdown, when there is nothing to apply (02 §1-1 guards).
     */
    public static Result apply(JsonNode response, Map<String, Double> baseMap, TravelDayOpts opts,
                               String periodStart, String periodEnd) {
        TreeMap<String, Double> base = new TreeMap<>();
        if (baseMap != null) {
            base.putAll(baseMap);
        } else if (response.path("limitAmounts").isObject()) {
            response.path("limitAmounts").fields().forEachRemaining(e -> base.put(e.getKey(), e.getValue().asDouble(0)));
        } else {
            return new Result(base, null);                               // baseMap null -> pass-through
        }
        JsonNode conditions = response.path("appliedConditions");
        if (!conditions.isArray() || conditions.isEmpty()) {
            return new Result(base, null);                               // :319
        }
        JsonNode enabled = response.get("calcEnabled");
        if (enabled != null && enabled.isBoolean() && !enabled.asBoolean()) {
            return new Result(base, null);                               // :320 calcEnabled === false
        }
        TravelDayOpts o = opts == null ? TravelDayOpts.NONE : opts;
        double limitAmount = response.path("limitAmount").asDouble(0);

        // Candidates: matched only, ascending sortOrder, stable (02 §1-1; overridden is per day).
        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode c : conditions) {
            if (c.path("matched").asBoolean(false)) {
                candidates.add(c);
            }
        }
        candidates.sort((a, b) -> Double.compare(a.path("sortOrder").asDouble(0), b.path("sortOrder").asDouble(0)));
        if (candidates.isEmpty()) {
            ObjectNode breakdown = JsonNodeFactory.instance.objectNode();
            breakdown.put("baseAmount", limitAmount);
            breakdown.putArray("items");
            breakdown.put("totalAmount", sum(base));
            return new Result(base, breakdown);                          // :335-340 map untouched
        }

        List<String> dates = new ArrayList<>(base.keySet());              // TreeMap: ISO sort = chronological
        Positions positions = resolveDayPositions(dates, o.preCount(), o.postCount(), periodStart, periodEnd);

        // 02 §5-2: three-way split when travel days can be assigned, else everything is core.
        List<JsonNode> preConds = new ArrayList<>();
        List<JsonNode> postConds = new ArrayList<>();
        List<JsonNode> coreConds = new ArrayList<>();
        Map<JsonNode, String> slotOf = new HashMap<>();                   // for the effect label
        if (positions.travelDayAssignable()) {
            for (JsonNode c : candidates) {
                boolean pre = hasItemType(c, o.predepartType());
                boolean post = hasItemType(c, o.nextarriveType());
                if (pre) {
                    preConds.add(c);
                    slotOf.put(c, "pre");
                }
                if (post) {
                    postConds.add(c);
                    slotOf.put(c, "post");
                }
                if (!pre && !post) {
                    coreConds.add(c);
                }
            }
        } else {
            coreConds.addAll(candidates);
        }

        JsonNode dayTypeMap = response.path("dayTypeMap");
        TreeMap<String, Double> finalMap = new TreeMap<>();
        Set<JsonNode> applied = new LinkedHashSet<>();
        int index = 0;
        for (String date : dates) {
            double amount = base.getOrDefault(date, 0d);
            DayPosition pos = positions.byDate().get(date);
            int dayNum = index + 1;                                        // map index, NOT the absolute date (02 §3-3)
            index++;
            if (pos == null) {
                finalMap.put(date, amount);
                continue;
            }
            double value;
            if ("pre".equals(pos.slot())) {
                value = applyConditionsForDay(preConds, date, amount, dayTypeMap, true, false, dayNum, null, applied);
            } else if ("post".equals(pos.slot())) {
                value = applyConditionsForDay(postConds, date, amount, dayTypeMap, false, true, dayNum, null, applied);
            } else {
                value = applyConditionsForDay(coreConds, date, amount, dayTypeMap, pos.isFirst(), pos.isLast(), dayNum, pos, applied);
            }
            finalMap.put(date, value);
        }

        List<JsonNode> appliedSorted = new ArrayList<>(applied);
        appliedSorted.sort((a, b) -> Double.compare(a.path("sortOrder").asDouble(0), b.path("sortOrder").asDouble(0)));
        ObjectNode breakdown = JsonNodeFactory.instance.objectNode();
        breakdown.put("baseAmount", effectiveBaseAmount(appliedSorted, limitAmount));
        ArrayNode items = breakdown.putArray("items");
        for (JsonNode c : appliedSorted) {
            ObjectNode item = items.addObject();
            item.put("label", label(c));
            item.put("effect", effect(c, slotOf.get(c)));
        }
        breakdown.put("totalAmount", sum(finalMap));
        return new Result(finalMap, breakdown);
    }

    /** 02 §6-1: does this response need the convert-then-apply path (a foreign 규정 or operand)? */
    public static boolean needsKrwConversion(JsonNode response) {
        JsonNode enabled = response.get("calcEnabled");
        if (enabled != null && enabled.isBoolean() && !enabled.asBoolean()) {
            return false;
        }
        boolean active = false;
        for (JsonNode c : response.path("appliedConditions")) {
            if (c.path("matched").asBoolean(false) && !c.path("overridden").asBoolean(false)) {
                active = true;
            }
        }
        if (isForeign(response.path("currencyCode").asText(""))) {
            return active;
        }
        return !operandCurrencies(response).isEmpty();
    }

    /** 02 §6-1 collectDiffCurrencies: the foreign currencies the ACTIVE conditions' amounts carry. */
    public static Set<String> operandCurrencies(JsonNode response) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode c : response.path("appliedConditions")) {
            if (!c.path("matched").asBoolean(false) || c.path("overridden").asBoolean(false)) {
                continue;
            }
            String op = normalizeOperator(c.path("operator"));
            if (("fixed".equals(op) || "silbiLimit".equals(op)) && isForeign(c.path("fixedCurrency").asText(""))) {
                out.add(c.path("fixedCurrency").asText().trim().toUpperCase());
            }
            if ("dailyDiff".equals(normalizeMethod(c.path("calcMethod")))
                    && isForeign(c.path("details").path("diffCurrency").asText(""))) {
                out.add(c.path("details").path("diffCurrency").asText().trim().toUpperCase());
            }
        }
        return out;
    }

    /**
     * 02 §6-2 applyDiffCurrencyToRule: a copy of the response whose foreign condition amounts
     * ({@code fixedAmount}, {@code diffBaseAmt}, {@code diff*Amt}) are converted to KRW with the
     * given rate per currency (KRW per one unit). The three currency axes are independent.
     */
    public static ObjectNode withOperandsInKrw(JsonNode response, Map<String, Double> krwPerUnit) {
        ObjectNode copy = response.deepCopy();
        for (JsonNode c : copy.path("appliedConditions")) {
            ObjectNode cond = (ObjectNode) c;
            String op = normalizeOperator(cond.path("operator"));
            String fixedCurrency = cond.path("fixedCurrency").asText("").trim().toUpperCase();
            if (("fixed".equals(op) || "silbiLimit".equals(op)) && isForeign(fixedCurrency)
                    && cond.path("fixedAmount").isNumber() && krwPerUnit.containsKey(fixedCurrency)) {
                cond.put("fixedAmount", cond.path("fixedAmount").asDouble() * krwPerUnit.get(fixedCurrency));
                cond.put("fixedCurrency", "KRW");
            }
            JsonNode details = cond.path("details");
            String diffCurrency = details.path("diffCurrency").asText("").trim().toUpperCase();
            if (details.isObject() && isForeign(diffCurrency) && krwPerUnit.containsKey(diffCurrency)) {
                double rate = krwPerUnit.get(diffCurrency);
                ObjectNode d = (ObjectNode) details;
                for (String key : new String[]{"diffBaseAmt", "diffFirstAmt", "diffMidAmt", "diffLastAmt"}) {
                    if (d.path(key).isNumber()) {
                        d.put(key, d.path(key).asDouble() * rate);
                    }
                }
                d.put("diffCurrency", "KRW");
            }
        }
        return copy;
    }

    public static boolean isForeign(String currency) {
        return currency != null && !currency.isBlank() && !"KRW".equalsIgnoreCase(currency.trim());
    }

    // ---- one day -------------------------------------------------------------------------

    private static double applyConditionsForDay(List<JsonNode> conds, String date, double amount, JsonNode dayTypeMap,
                                                boolean isFirst, boolean isLast, int dayNum, DayPosition pos,
                                                Set<JsonNode> applied) {
        Set<Long> applicableIds = new HashSet<>();
        for (JsonNode c : conds) {
            if (c.hasNonNull("id") && isApplicableOnDay(c, date, dayTypeMap, dayNum)) {
                applicableIds.add(c.path("id").asLong());
            }
        }
        double result = amount;
        for (JsonNode c : conds) {
            if (!isApplicableOnDay(c, date, dayTypeMap, dayNum)) {
                continue;                                                // skip: amount keeps its state
            }
            if (isSuppressedOnDay(c, applicableIds)) {
                continue;
            }
            applied.add(c);
            if (pos != null) {
                result = isDayCovered(c, pos) ? calculateAmount(c, result, isFirst, isLast) : 0;   // 0, not skip (:228)
            } else {
                result = calculateAmount(c, result, isFirst, isLast);   // travel day: no coverage check
            }
        }
        return result;
    }

    private static boolean isApplicableOnDay(JsonNode c, String date, JsonNode dayTypeMap, int dayNum) {
        return isDayTypeMatched(c, dayTypeMap.path(date))
                && isDateInRange(c, date)
                && isPeriodDayCovered(c, dayNum);
    }

    /** 02 §3-1: string equality against the day's type; the wildcard and empty values never constrain. */
    private static boolean isDayTypeMatched(JsonNode c, JsonNode dayType) {
        for (JsonNode item : c.path("items")) {
            if (!DAY_TYPE_ITEM.contains(item.path("itemType").asText(""))) {
                continue;
            }
            String value = item.path("itemValue").asText("");
            if (value.isEmpty() || DAY_TYPE_ALWAYS.equals(value)) {
                continue;
            }
            if (dayType == null || dayType.isMissingNode() || dayType.isNull() || !value.equals(dayType.asText())) {
                return false;                                            // includes a missing day
            }
        }
        return true;
    }

    /** 02 §3-2: the date option - ISO strings compare safely. */
    private static boolean isDateInRange(JsonNode c, String date) {
        JsonNode range = c.path("details").path("dateRange");
        if (!range.isObject()) {
            return true;
        }
        String from = range.path("from").asText("");
        String to = range.path("to").asText("");
        if (!from.isEmpty() && date.compareTo(from) < 0) {
            return false;
        }
        return to.isEmpty() || date.compareTo(to) <= 0;
    }

    /** 02 §3-3: the period option against the 1-based MAP index. */
    private static boolean isPeriodDayCovered(JsonNode c, int dayNum) {
        JsonNode pr = c.path("details").path("periodRange");
        if (!pr.isObject() || isFalsy(pr.path("value"))) {
            return true;
        }
        double value = toNumber(pr.path("value"));
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return true;
        }
        return switch (pr.path("op").asText("")) {
            case "gt" -> dayNum > value;
            case "lte" -> dayNum <= value;
            case "lt" -> dayNum < value;
            default -> dayNum >= value;                                  // gte and unspecified
        };
    }

    /** 02 §1-3: newer responses yield per day through supersededByIds; older ones globally via overridden. */
    private static boolean isSuppressedOnDay(JsonNode c, Set<Long> applicableIds) {
        JsonNode superseders = c.get("supersededByIds");
        if (superseders == null || superseders.isNull()) {
            return c.path("overridden").asBoolean(false);
        }
        for (JsonNode id : superseders) {
            if (applicableIds.contains(id.asLong())) {
                return true;
            }
        }
        return false;
    }

    /** 02 §3-4: payment-period coverage on a core day - false means the day's amount becomes 0. */
    private static boolean isDayCovered(JsonNode c, DayPosition pos) {
        return switch (normalizeMethod(c.path("calcMethod"))) {
            case "departArrive" -> pos.isFirst() || pos.isLast();
            case "excludeDepart" -> !pos.isFirst();
            case "excludeArrive" -> !pos.isLast();
            case "excludeN" -> {
                long excludeDays = c.path("excludeDays").isNumber() ? c.path("excludeDays").asLong() : 0;   // null -> 0
                yield excludeDays >= pos.coreTotalDays() ? false : pos.dayFromEnd() >= excludeDays;
            }
            default -> true;                                             // daily, dailyDiff, unknown
        };
    }

    private static double calculateAmount(JsonNode c, double amount, boolean isFirst, boolean isLast) {
        if ("dailyDiff".equals(normalizeMethod(c.path("calcMethod")))) {
            return applyDailyDiff(c, amount, isFirst, isLast);           // operator ignored (02 §4)
        }
        return applyOperator(c, amount);
    }

    // ---- 02 §2: the 8 operators -----------------------------------------------------------

    private static double applyOperator(JsonNode c, double amount) {
        double v = parseValue(c.path("operatorValue"));
        switch (normalizeOperator(c.path("operator"))) {
            case "none":
                return amount;
            case "unpaid":
                return 0;
            case "fixed":
            case "silbiLimit":                                           // a replacement, not a min
                return c.path("fixedAmount").isNumber() ? c.path("fixedAmount").asDouble() : amount;
            case "x": {
                String unit = c.path("operatorUnit").asText("");
                boolean percent = !c.path("operatorUnit").isNull() && PERCENT_UNITS.contains(unit);
                return percent ? amount * (v / 100) : amount * v;
            }
            case "+":
                return amount + v;
            case "-":
                return Math.max(0, amount - v);                          // the only clamp
            case "/":
                return v != 0 ? amount / v : amount;
            default:
                return amount;                                           // unknown operator: unchanged
        }
    }

    static String normalizeOperator(JsonNode node) {
        String op = node == null || node.isNull() ? "" : node.asText("");
        if (op.isEmpty()) {
            return "none";
        }
        return switch (op) {
            case "NONE" -> "none";
            case "FIXED" -> "fixed";
            case "UNPAID" -> "unpaid";
            case "ADD" -> "+";
            case "SUBTRACT" -> "-";
            case "MULTIPLY" -> "x";
            case "DIVIDE" -> "/";
            default -> op;                                               // verbatim (no SILBI_LIMIT alias)
        };
    }

    static String normalizeMethod(JsonNode node) {
        String m = node == null || node.isNull() ? "" : node.asText("");
        if (m.isEmpty()) {
            return "daily";
        }
        return switch (m) {
            case "DAILY" -> "daily";
            case "DAILY_DIFF" -> "dailyDiff";
            case "DEPART_ARRIVE" -> "departArrive";
            case "EXCLUDE_DEPART" -> "excludeDepart";
            case "EXCLUDE_ARRIVE" -> "excludeArrive";
            case "EXCLUDE_N" -> "excludeN";
            default -> m;                                                // verbatim, never flattened to daily
        };
    }

    /** 02 §2-4 parseValue: null -> 0, leading number (JS parseFloat), NaN -> 0. */
    private static double parseValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return 0;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        double n = parseFloat(node.asText(""));
        return Double.isNaN(n) ? 0 : n;
    }

    /** JS Number.parseFloat: the leading number of the string, NaN when there is none. */
    private static double parseFloat(String text) {
        if (text == null) {
            return Double.NaN;
        }
        Matcher m = LEADING_NUMBER.matcher(text);
        if (!m.find()) {
            String t = text.trim();
            if (t.startsWith("Infinity") || t.startsWith("+Infinity")) {
                return Double.POSITIVE_INFINITY;
            }
            if (t.startsWith("-Infinity")) {
                return Double.NEGATIVE_INFINITY;
            }
            return Double.NaN;
        }
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ---- 02 §4: dailyDiff -------------------------------------------------------------------

    private static double applyDailyDiff(JsonNode c, double amount, boolean isFirst, boolean isLast) {
        JsonNode details = c.path("details");
        JsonNode baseNode = details.path("diffBaseAmt");
        boolean hasBase = baseNode.isNumber();                           // ?? lets 0 through, null does not
        double base = hasBase ? baseNode.asDouble() : amount;
        if ("amt".equals(details.path("diffType").asText(""))) {
            JsonNode diff = isFirst ? details.path("diffFirstAmt") : isLast ? details.path("diffLastAmt") : details.path("diffMidAmt");
            if (!diff.isNumber() || Double.isNaN(diff.asDouble()) || Double.isInfinite(diff.asDouble())) {
                return Math.max(0, base);                                // no adjustment: base kept
            }
            return Math.max(0, base + diff.asDouble());
        }
        JsonNode rateNode = isFirst ? c.path("diffFirst") : isLast ? c.path("diffLast") : c.path("diffMid");
        if (isFalsy(rateNode)) {
            return amount;                                               // '' / null: keep the base, not x0
        }
        double rate = rateNode.isNumber() ? rateNode.asDouble() : parseFloat(rateNode.asText(""));
        if (Double.isNaN(rate)) {
            return amount;
        }
        return base * (rate / 100);                                      // no clamp in pct mode
    }

    // ---- 02 §3-5: day positions -------------------------------------------------------------

    private static Positions resolveDayPositions(List<String> dates, int preCount, int postCount,
                                                 String periodStart, String periodEnd) {
        Map<String, DayPosition> out = new LinkedHashMap<>();
        boolean absolute = isYmd(periodStart) && isYmd(periodEnd);
        if (absolute) {
            for (String d : dates) {
                if (!isYmd(d) || d.compareTo(periodStart) < 0 || d.compareTo(periodEnd) > 0) {
                    absolute = false;                                    // map must sit inside the period
                    break;
                }
            }
        }
        if (absolute) {
            LocalDate start = LocalDate.parse(periodStart);
            LocalDate end = LocalDate.parse(periodEnd);
            LocalDate rawCoreStart = start.plusDays(preCount);
            LocalDate rawCoreEnd = end.minusDays(postCount);
            boolean assignable = (preCount > 0 || postCount > 0) && !rawCoreEnd.isBefore(rawCoreStart);
            LocalDate coreStart = assignable ? rawCoreStart : start;
            LocalDate coreEnd = assignable ? rawCoreEnd : end;
            long coreTotalDays = ChronoUnit.DAYS.between(coreStart, coreEnd) + 1;
            for (String d : dates) {
                LocalDate day = LocalDate.parse(d);
                String slot = day.isBefore(coreStart) ? "pre" : day.isAfter(coreEnd) ? "post" : "core";
                boolean isFirst = "pre".equals(slot) || ("core".equals(slot) && day.equals(coreStart));
                boolean isLast = "post".equals(slot) || ("core".equals(slot) && day.equals(coreEnd));
                out.put(d, new DayPosition(slot, isFirst, isLast, ChronoUnit.DAYS.between(day, coreEnd), coreTotalDays));
            }
            return new Positions(out, assignable);
        }
        int lastIndex = dates.size() - 1;
        boolean assignable = (preCount > 0 || postCount > 0) && preCount + postCount <= lastIndex;
        int coreStart = assignable ? preCount : 0;
        int coreEnd = assignable ? lastIndex - postCount : lastIndex;
        long coreTotalDays = coreEnd - coreStart + 1L;
        for (int i = 0; i < dates.size(); i++) {
            String slot = i < coreStart ? "pre" : i > coreEnd ? "post" : "core";
            boolean isFirst = "pre".equals(slot) || ("core".equals(slot) && i == coreStart);
            boolean isLast = "post".equals(slot) || ("core".equals(slot) && i == coreEnd);
            out.put(dates.get(i), new DayPosition(slot, isFirst, isLast, coreEnd - i, coreTotalDays));
        }
        return new Positions(out, assignable);
    }

    /** 02 §5-2 hasItemType: String(item.itemType) === t; a falsy t never matches. */
    private static boolean hasItemType(JsonNode c, String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        for (JsonNode item : c.path("items")) {
            if (type.equals(item.path("itemType").asText(""))) {
                return true;
            }
        }
        return false;
    }

    // ---- 02 §7: the calculation detail -----------------------------------------------------

    /** 02 §7-2: the last dailyDiff condition's diffBaseAmt (0 included), else the rule's limitAmount. */
    private static double effectiveBaseAmount(List<JsonNode> applied, double fallback) {
        double effective = fallback;
        for (JsonNode c : applied) {
            if (!"dailyDiff".equals(normalizeMethod(c.path("calcMethod")))) {
                continue;
            }
            JsonNode base = c.path("details").path("diffBaseAmt");
            if (base.isNumber()) {
                effective = base.asDouble();
            }
        }
        return effective;
    }

    /** buildLabel: the condition items' values joined with '·', '기본' when there are none. */
    static String label(JsonNode c) {
        List<String> parts = new ArrayList<>();
        for (JsonNode item : c.path("items")) {
            String v = item.path("itemValue").asText("");
            if (v.isEmpty() && DAY_TYPE_ITEM.contains(item.path("itemType").asText(""))) {
                v = DAY_TYPE_ALWAYS;                                     // an unconstrained day type reads as the wildcard
            }
            if (!v.isEmpty()) {
                parts.add(v);
            }
        }
        return parts.isEmpty() ? "기본" : String.join("·", parts);
    }

    /** buildEffect: the operation's meaning in one line, in the FE's own wording (02 §7-1). */
    static String effect(JsonNode c, String slot) {
        JsonNode details = c.path("details");
        String baseSuffix = details.path("diffBaseAmt").isNumber() ? ", 기준 " + won(details.path("diffBaseAmt").asDouble()) : "";
        if ("dailyDiff".equals(normalizeMethod(c.path("calcMethod")))) {
            boolean amt = "amt".equals(details.path("diffType").asText(""));
            if ("pre".equals(slot) || "post".equals(slot)) {
                // A travel-day condition uses one tier only - the effect names just that one.
                String head = "pre".equals(slot) ? "전일출발일 " : "익일도착일 ";
                if (amt) {
                    JsonNode tier = "pre".equals(slot) ? details.path("diffFirstAmt") : details.path("diffLastAmt");
                    return head + signedWon(tier) + baseSuffix;
                }
                JsonNode tier = "pre".equals(slot) ? c.path("diffFirst") : c.path("diffLast");
                return head + pct(tier) + "%" + baseSuffix;
            }
            if (amt) {
                return "차등 " + signedWon(details.path("diffFirstAmt")) + "/" + signedWon(details.path("diffMidAmt"))
                        + "/" + signedWon(details.path("diffLastAmt")) + baseSuffix;
            }
            return "차등 " + pct(c.path("diffFirst")) + "/" + pct(c.path("diffMid")) + "/" + pct(c.path("diffLast")) + "%" + baseSuffix;
        }
        JsonNode raw = c.path("operatorValue");
        String value = raw.isNull() || raw.isMissingNode() ? "" : raw.asText("");
        String number = value.isBlank() ? "" : MONEY.format(parseValue(raw));
        return switch (normalizeOperator(c.path("operator"))) {
            case "none" -> "변경없음";
            case "unpaid" -> "미지급(0원)";
            case "fixed" -> "정액 " + won(c.path("fixedAmount").isNumber() ? c.path("fixedAmount").asDouble() : 0);
            case "silbiLimit" -> "실비(제한) " + won(c.path("fixedAmount").isNumber() ? c.path("fixedAmount").asDouble() : 0);
            case "x" -> {
                boolean percent = !c.path("operatorUnit").isNull() && PERCENT_UNITS.contains(c.path("operatorUnit").asText(""));
                yield percent ? "×" + number + "%" : "×" + number + "배";
            }
            case "+" -> "+" + number + "원";
            case "-" -> "-" + number + "원";
            case "/" -> "÷" + number;
            default -> "변경없음";
        };
    }

    private static String won(double v) {
        return MONEY.format(v) + "원";
    }

    private static String signedWon(JsonNode tier) {
        double v = tier.isNumber() ? tier.asDouble() : 0;
        return (v > 0 ? "+" : "") + MONEY.format(v) + "원";
    }

    private static String pct(JsonNode tier) {
        if (isFalsy(tier)) {
            return "0";
        }
        double v = tier.isNumber() ? tier.asDouble() : parseFloat(tier.asText(""));
        return Double.isNaN(v) ? "0" : MONEY.format(v);
    }

    // ---- small helpers ----------------------------------------------------------------------

    /** JS falsiness for a JSON value: null, missing, '', 0, false. */
    private static boolean isFalsy(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return true;
        }
        if (node.isNumber()) {
            return node.asDouble() == 0;
        }
        if (node.isBoolean()) {
            return !node.asBoolean();
        }
        return node.asText("").isEmpty();
    }

    private static double toNumber(JsonNode node) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            return Double.parseDouble(node.asText("").trim());           // JS Number(): whole string
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static boolean isYmd(String s) {
        if (s == null || !YMD.matcher(s).matches()) {
            return false;
        }
        try {
            LocalDate.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double sum(Map<String, Double> map) {
        double s = 0;
        for (double v : map.values()) {
            s += v;
        }
        return s;
    }
}
