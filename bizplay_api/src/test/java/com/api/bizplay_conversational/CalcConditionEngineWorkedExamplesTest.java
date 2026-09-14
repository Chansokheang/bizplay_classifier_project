package com.api.bizplay_conversational;

import com.api.bizplay_conversational.service.ruledAmountLookupService.CalcConditionEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 규정금액 layer ② against the three worked examples of 증빙 규정금액 산출/03_WorkedExamples.md,
 * row by row: engine per-day amounts, the calculation detail, and the receipt-stage sum
 * (usage-period filter, check-out exclusion, per-date trunc) that their tables end with.
 */
class CalcConditionEngineWorkedExamplesTest {

    private static final ObjectMapper M = new ObjectMapper();

    // ---- Example A: domestic lodging, 평일 ×80% then 단기 ×50%, check-out day excluded ----------

    private static final String A_RESPONSE = """
        {
          "id": 88120, "limitAmount": 100000,
          "limitAmounts": {"2026-08-27": 100000, "2026-08-28": 100000, "2026-08-29": 100000, "2026-08-30": 100000},
          "dayTypeMap": {"2026-08-27": "평일", "2026-08-28": "평일", "2026-08-29": "주말", "2026-08-30": "주말"},
          "currencyCode": "KRW", "tranKindType": "ROOM", "tranKindId": 3301,
          "bstrPayClassType": "FIXED", "bstrPayOptionType": "ALL", "bstrCategoryType": "MONEY", "calcEnabled": true,
          "appliedConditions": [
            {"id": 11, "sortOrder": 1, "calcMethod": "daily", "operator": "x", "operatorValue": "80", "operatorUnit": "PERCENT",
             "fixedAmount": null, "fixedCurrency": null, "diffFirst": null, "diffMid": null, "diffLast": null, "excludeDays": null,
             "details": {}, "items": [{"itemType": "dayType", "itemValue": "평일", "sortOrder": 1}],
             "matched": true, "overridden": false, "supersededByIds": []},
            {"id": 12, "sortOrder": 2, "calcMethod": "daily", "operator": "x", "operatorValue": "50", "operatorUnit": "PERCENT",
             "fixedAmount": null, "fixedCurrency": null, "diffFirst": null, "diffMid": null, "diffLast": null, "excludeDays": null,
             "details": {}, "items": [{"itemType": "SEGMENT", "itemValue": "단기", "sortOrder": 1}],
             "matched": true, "overridden": false, "supersededByIds": []}
          ]
        }
        """;

    @Test
    void exampleA_lodgingWeekdayThenSegment() throws Exception {
        JsonNode res = M.readTree(A_RESPONSE);
        CalcConditionEngine.Result r = CalcConditionEngine.apply(res, null, null, "2026-08-27", "2026-08-30");

        // A-4 engine output row
        assertEquals(Map.of("2026-08-27", 40000.0, "2026-08-28", 40000.0, "2026-08-29", 50000.0, "2026-08-30", 50000.0),
                r.limitAmounts());
        // A-4 calcBreakdown
        assertNotNull(r.calcBreakdown());
        assertEquals(100000.0, r.calcBreakdown().path("baseAmount").asDouble());
        assertEquals(180000.0, r.calcBreakdown().path("totalAmount").asDouble());
        assertEquals("×80%", r.calcBreakdown().path("items").get(0).path("effect").asText());
        assertEquals("평일", r.calcBreakdown().path("items").get(0).path("label").asText());
        assertEquals("×50%", r.calcBreakdown().path("items").get(1).path("effect").asText());
        // A-4 receipt row: 08-27..08-30 lodging, check-out day excluded -> 3 nights
        assertEquals(130000.0, receiptSum(r.limitAmounts(), "2026-08-27", "2026-08-30", true));
        // Pitfall 3: the breakdown total is not the receipt amount
        assertEquals(180000.0, receiptSum(r.limitAmounts(), "2026-08-27", "2026-08-30", false));
    }

    // ---- Example B: per diem with travel days, dailyDiff pct on diffBaseAmt 30,000 -------------

    private static final String B_RESPONSE = """
        {
          "limitAmount": 90606,
          "limitAmounts": {"2026-08-06": 90606, "2026-08-07": 90606, "2026-08-08": 90606, "2026-08-09": 90606},
          "dayTypeMap": {"2026-08-06": "평일", "2026-08-07": "평일", "2026-08-08": "평일", "2026-08-09": "평일"},
          "currencyCode": "KRW", "tranKindType": "DAILY_COST", "bstrPayClassType": "FIXED", "bstrPayOptionType": "ALL",
          "calcEnabled": true,
          "appliedConditions": [
            {"id": 1775, "sortOrder": 2, "calcMethod": "dailyDiff", "operator": "none", "operatorValue": null, "operatorUnit": null,
             "fixedAmount": null, "diffFirst": "100", "diffMid": "100", "diffLast": "50", "excludeDays": null,
             "details": {"diffType": "pct", "diffBaseAmt": 30000},
             "items": [{"itemType": "260", "itemValue": "익일도착", "sortOrder": 1}], "matched": true, "overridden": false},
            {"id": 1776, "sortOrder": 3, "calcMethod": "dailyDiff", "operator": "none", "operatorValue": null, "operatorUnit": null,
             "fixedAmount": null, "diffFirst": "50", "diffMid": "100", "diffLast": "100", "excludeDays": null,
             "details": {"diffType": "pct", "diffBaseAmt": 30000},
             "items": [{"itemType": "259", "itemValue": "전일출발", "sortOrder": 2}], "matched": true, "overridden": false}
          ]
        }
        """;

    @Test
    void exampleB_perDiemWithTravelDays() throws Exception {
        JsonNode res = M.readTree(B_RESPONSE);
        CalcConditionEngine.TravelDayOpts opts = new CalcConditionEngine.TravelDayOpts(1, 1, "259", "260");
        CalcConditionEngine.Result r = CalcConditionEngine.apply(res, null, opts, "2026-08-06", "2026-08-09");

        // B-4 engine output row (test-measured values)
        assertEquals(Map.of("2026-08-06", 15000.0, "2026-08-07", 90606.0, "2026-08-08", 90606.0, "2026-08-09", 15000.0),
                r.limitAmounts());
        // B-4 calcBreakdown: base is the tiered base, not the rule base
        assertEquals(30000.0, r.calcBreakdown().path("baseAmount").asDouble());
        assertEquals(211212.0, r.calcBreakdown().path("totalAmount").asDouble());
        JsonNode items = r.calcBreakdown().path("items");
        assertEquals(2, items.size());
        assertEquals("익일도착", items.get(0).path("label").asText());
        assertEquals("익일도착일 50%, 기준 30,000원", items.get(0).path("effect").asText());
        assertEquals("전일출발", items.get(1).path("label").asText());
        assertEquals("전일출발일 50%, 기준 30,000원", items.get(1).path("effect").asText());
        // B-4 receipt row: all 4 days eligible, per-date trunc then sum
        assertEquals(211212.0, receiptSum(r.limitAmounts(), "2026-08-06", "2026-08-09", false));

        // B-6 wrong-answer check ②: without travel-day opts both conditions fall back to core
        CalcConditionEngine.Result noOpts = CalcConditionEngine.apply(res, null, null, "2026-08-06", "2026-08-09");
        assertEquals(Map.of("2026-08-06", 15000.0, "2026-08-07", 30000.0, "2026-08-08", 30000.0, "2026-08-09", 30000.0),
                noOpts.limitAmounts());
        assertEquals(105000.0, noOpts.calcBreakdown().path("totalAmount").asDouble());

        // Pitfall 9 (as golden vector G-06 pins it): a type that matches nothing sends both conditions
        // to the core; the pre/post days themselves keep their base amount untouched
        CalcConditionEngine.Result nullTypes = CalcConditionEngine.apply(res, null,
                new CalcConditionEngine.TravelDayOpts(1, 1, null, null), "2026-08-06", "2026-08-09");
        assertEquals(Map.of("2026-08-06", 90606.0, "2026-08-07", 15000.0, "2026-08-08", 30000.0, "2026-08-09", 90606.0),
                nullTypes.limitAmounts());

        // Pitfall 11: pre+post eat the whole map -> assignment abandoned, all days core
        String twoDays = B_RESPONSE.replace(
                "\"limitAmounts\": {\"2026-08-06\": 90606, \"2026-08-07\": 90606, \"2026-08-08\": 90606, \"2026-08-09\": 90606}",
                "\"limitAmounts\": {\"2026-08-07\": 90606, \"2026-08-08\": 90606}");
        CalcConditionEngine.Result noRoom = CalcConditionEngine.apply(M.readTree(twoDays), null, opts, "2026-08-07", "2026-08-08");
        assertEquals(Map.of("2026-08-07", 15000.0, "2026-08-08", 30000.0), noRoom.limitAmounts());
    }

    // ---- Example C: foreign-currency meal, convert first then + 50,000 KRW ----------------------

    private static final String C_RESPONSE = """
        {
          "limitAmount": 100,
          "limitAmounts": {"2026-08-27": 100, "2026-08-28": 100},
          "dayTypeMap": {"2026-08-27": "평일", "2026-08-28": "평일"},
          "currencyCode": "USD", "tranKindType": "FOOD", "tranKindId": 2201,
          "bstrPayClassType": "FIXED", "bstrPayOptionType": "ALL", "calcEnabled": true,
          "appliedConditions": [
            {"id": 21, "sortOrder": 1, "calcMethod": "daily", "operator": "+", "operatorValue": "50000", "operatorUnit": null,
             "fixedAmount": null, "fixedCurrency": null, "diffFirst": null, "diffMid": null, "diffLast": null, "excludeDays": null,
             "details": {}, "items": [{"itemType": "dayType", "itemValue": "평일", "sortOrder": 1}],
             "matched": true, "overridden": false}
          ]
        }
        """;

    @Test
    void exampleC_foreignMealConvertThenApply() throws Exception {
        JsonNode res = M.readTree(C_RESPONSE);
        // C-4: the response needs the convert-then-apply path, no condition operand is foreign
        assertEquals(true, CalcConditionEngine.needsKrwConversion(res));
        assertEquals(0, CalcConditionEngine.operandCurrencies(res).size());
        // C-4 conversion row: trunc(100 × 1300) per date (exchangeToKRW)
        Map<String, Double> krwBase = new LinkedHashMap<>();
        res.path("limitAmounts").fields().forEachRemaining(e -> krwBase.put(e.getKey(), Math.floor(e.getValue().asDouble() * 1300)));
        assertEquals(Map.of("2026-08-27", 130000.0, "2026-08-28", 130000.0), krwBase);

        CalcConditionEngine.Result r = CalcConditionEngine.apply(res, krwBase, null, "2026-08-27", "2026-08-28");
        // C-4 engine output row: both days are weekdays
        assertEquals(Map.of("2026-08-27", 180000.0, "2026-08-28", 180000.0), r.limitAmounts());
        // (their document gives no calcBreakdown for C; the J-vectors pin none either - only the label is checked)
        assertEquals("평일", r.calcBreakdown().path("items").get(0).path("label").asText());
        // C-5 receipt row: dinner on 08-27 only
        assertEquals(180000.0, receiptSum(r.limitAmounts(), "2026-08-27", "2026-08-27", false));
        // Pitfall 19: a non-overlapping usage period must NOT fall back to the whole map (360,000)
        assertNull(receiptSumOrNull(r.limitAmounts(), "2026-09-01", "2026-09-01"));

        // Pitfall 15: without dayTypeMap the dayType condition matches nothing
        JsonNode noDayTypes = M.readTree(C_RESPONSE.replace("\"dayTypeMap\": {\"2026-08-27\": \"평일\", \"2026-08-28\": \"평일\"},", ""));
        CalcConditionEngine.Result skipped = CalcConditionEngine.apply(noDayTypes, krwBase, null, "2026-08-27", "2026-08-28");
        assertEquals(Map.of("2026-08-27", 130000.0, "2026-08-28", 130000.0), skipped.limitAmounts());

        // C-6 wrong-answer check: applying the condition on the USD base then converting balloons ×362
        CalcConditionEngine.Result wrongOrder = CalcConditionEngine.apply(res, null, null, "2026-08-27", "2026-08-28");
        assertEquals(65130000.0, Math.floor(wrongOrder.limitAmounts().get("2026-08-27") * 1300));
    }

    // ---- receipt stage as their tables define it -----------------------------------------------

    /** Usage-period sum with per-date {@code Math.trunc}; lodging drops the check-out day. */
    private static double receiptSum(TreeMap<String, Double> map, String start, String end, boolean lodging) {
        Double v = receiptSumOrNull(map, start, lodging ? LocalDate.parse(end).minusDays(1).toString() : end);
        assertNotNull(v, "no usage day overlaps the 규정 map");
        return v;
    }

    private static Double receiptSumOrNull(TreeMap<String, Double> map, String start, String end) {
        double sum = 0;
        int hit = 0;
        for (LocalDate d = LocalDate.parse(start); !d.isAfter(LocalDate.parse(end)); d = d.plusDays(1)) {
            Double v = map.get(d.toString());
            if (v != null) {
                sum += (long) v.doubleValue();   // Math.trunc per date, then sum (02 §6, 03 pitfall 14)
                hit++;
            }
        }
        return hit == 0 ? null : sum;
    }

    @SuppressWarnings("unused")
    private static List<String> keys(Map<String, ?> m) {
        return List.copyOf(m.keySet());
    }
}
