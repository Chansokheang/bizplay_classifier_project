package com.api.bizplay_conversational;

import com.api.bizplay_conversational.service.ruledAmountLookupService.CalcConditionEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 규정금액 layer ② against BizPlay's 71 golden vectors (증빙 규정금액 산출/규정금액_골든벡터.json):
 * the per-day amounts must match exactly; the calculation detail (base, total, item labels and
 * effects) is checked too, so a divergence names the rule that caused it.
 */
class CalcConditionEngineGoldenTest {

    private static final Path VECTORS = Path.of("src/main/resources/증빙 규정금액 산출/규정금액_골든벡터.json");

    @Test
    void allGoldenVectorsMatch() throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.readString(VECTORS));
        List<String> failures = new ArrayList<>();
        int amountMismatch = 0;
        int detailMismatch = 0;
        int count = 0;
        for (JsonNode v : root.path("vectors")) {
            count++;
            JsonNode input = v.path("input");
            Map<String, Double> base = null;
            if (v.has("krwBaseMap")) {
                base = new LinkedHashMap<>();
                for (var it = v.path("krwBaseMap").fields(); it.hasNext(); ) {
                    var e = it.next();
                    base.put(e.getKey(), e.getValue().asDouble());
                }
            }
            CalcConditionEngine.TravelDayOpts opts = null;
            if (v.has("opts")) {
                JsonNode o = v.path("opts");
                opts = new CalcConditionEngine.TravelDayOpts(o.path("preCount").asInt(0), o.path("postCount").asInt(0),
                        // G-06: a NUMBER here must not match (String(itemType) === t) - only text is a type
                        o.path("predepartType").isTextual() ? o.path("predepartType").asText() : null,
                        o.path("nextarriveType").isTextual() ? o.path("nextarriveType").asText() : null);
            }
            JsonNode period = input.path("calcPeriod");
            CalcConditionEngine.Result r = CalcConditionEngine.apply(input, base, opts,
                    period.path("start").asText(null), period.path("end").asText(null));

            String id = v.path("id").asText() + " [" + v.path("group").asText() + "] " + v.path("description").asText();
            JsonNode expected = v.path("expected");
            StringBuilder diff = new StringBuilder();
            JsonNode exAmounts = expected.path("limitAmounts");
            if (exAmounts.size() != r.limitAmounts().size()) {
                diff.append(" days ").append(r.limitAmounts().keySet()).append(" vs ").append(exAmounts);
            }
            for (var it = exAmounts.fields(); it.hasNext(); ) {
                var e = it.next();
                Double got = r.limitAmounts().get(e.getKey());
                if (got == null || Math.abs(got - e.getValue().asDouble()) > 1e-6) {
                    diff.append(" ").append(e.getKey()).append(": got ").append(got).append(" expected ").append(e.getValue());
                }
            }
            boolean amountsBad = diff.length() > 0;
            if (amountsBad) {
                amountMismatch++;
            }
            StringBuilder detail = new StringBuilder();
            JsonNode exTotal = expected.path("calcBreakdownTotalAmount");
            JsonNode exBase = expected.path("calcBreakdownBaseAmount");
            JsonNode exItems = expected.path("calcBreakdownItems");
            if (exTotal.isNull()) {
                if (r.calcBreakdown() != null) {
                    detail.append(" breakdown expected absent, got ").append(r.calcBreakdown());
                }
            } else if (r.calcBreakdown() == null) {
                detail.append(" breakdown expected ").append(exTotal).append("/").append(exBase).append(", got none");
            } else {
                if (Math.abs(r.calcBreakdown().path("totalAmount").asDouble() - exTotal.asDouble()) > 1e-6) {
                    detail.append(" total got ").append(r.calcBreakdown().path("totalAmount")).append(" expected ").append(exTotal);
                }
                if (Math.abs(r.calcBreakdown().path("baseAmount").asDouble() - exBase.asDouble()) > 1e-6) {
                    detail.append(" base got ").append(r.calcBreakdown().path("baseAmount")).append(" expected ").append(exBase);
                }
                JsonNode items = r.calcBreakdown().path("items");
                if (!items.equals(exItems)) {
                    detail.append(" items got ").append(items).append(" expected ").append(exItems);
                }
            }
            if (detail.length() > 0) {
                detailMismatch++;
            }
            if (amountsBad || detail.length() > 0) {
                failures.add(id + (amountsBad ? " | AMOUNTS:" + diff : "") + (detail.length() > 0 ? " | DETAIL:" + detail : ""));
            }
        }
        String report = count + " vectors, " + amountMismatch + " amount mismatch(es), " + detailMismatch
                + " detail mismatch(es)\n" + String.join("\n", failures);
        System.out.println(report);
        assertTrue(failures.isEmpty(), report);
    }
}
