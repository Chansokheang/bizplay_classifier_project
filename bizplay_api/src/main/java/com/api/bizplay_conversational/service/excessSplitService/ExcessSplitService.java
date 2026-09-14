package com.api.bizplay_conversational.service.excessSplitService;

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
import java.util.Set;

/**
 * 규정금액 layer ③, second half - the excess split (증빙 규정금액 산출/06_검증 §6.2.8, §6.5.8).
 * <p>
 * BizPlay's own screen refuses to file a settlement while any splittable receipt still carries
 * an excess (⑰), once the corp setting {@code splitPopupUsed} is on. The one remedy their system
 * offers is dividing the receipt into two {@code EXCESS} rows through
 * {@code PATCH /api/v2/receipt/divide/{receiptId}}: the 규정금액 row the company pays and the
 * excess row the traveller pays, posted to a debit account from the setting's
 * {@code allowedAccounts}. The request shape below was confirmed on cloud-dev on 2026-09-11
 * (receipt 342749: 60,000 spent on a 30,000 rule → rows 30,000 / 30,000, both accepted, read back
 * as two active {@code EXCESS} issued rows, undone with {@code divide/reset}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcessSplitService {

    /** 06 §6.5.8: what cannot be split on their screen is never blocked - nor split here. */
    private static final Set<String> RESERVATION_TYPES = Set.of("ACCOM", "FLIGHT", "TRANS");

    private final BizplayGatewayService bizplayGatewayService;
    private final ObjectMapper objectMapper;

    /** The two rows a receipt divides into (06 §6.2.8). */
    public record Plan(double used, double ruled, double policyClaim, double excess, double vatFirst) { }

    /** The corp's 초과금액 분할 setting; null when it cannot be read (then ⑰ does not run - 06 §6.5.8). */
    public JsonNode setting(String token) {
        try {
            return bizplayGatewayService.getExcessSplitSetting(token);
        } catch (RuntimeException e) {
            log.warn("[SPLIT] excessDebitSplit setting unavailable: {}", e.getMessage());
            return null;
        }
    }

    public static boolean enabled(JsonNode setting) {
        return setting != null && setting.path("splitPopupUsed").asBoolean(false);
    }

    /** The debit accounts the excess row may be posted to - the setting's whitelist. */
    public static List<JsonNode> allowedAccounts(JsonNode setting) {
        List<JsonNode> out = new ArrayList<>();
        if (setting != null) {
            for (JsonNode a : setting.path("allowedAccounts")) {
                if (a.path("accountSubjectId").asLong(0) > 0) {
                    out.add(a);
                }
            }
        }
        return out;
    }

    /** 06 §6.5.8 canSplitExcessOnScreen: not EXCESS / BASIC already, not automatic, not a reservation. */
    public static boolean splittable(JsonNode row) {
        String division = row.path("divisionType").asText("");
        if ("EXCESS".equals(division) || "BASIC".equals(division)) {
            return false;
        }
        if (row.path("documentBound").asBoolean(false)) {
            return false;
        }
        return !RESERVATION_TYPES.contains(row.path("additionalReceiptType").asText(""));
    }

    /**
     * 06 §6.2.8: the used amount (06 §6.1.3) against the line's 규정금액. The 규정금액 row claims
     * {@code MIN(used, ruled)}, the excess row {@code MAX(0, used − ruled)}; VAT sits entirely on
     * the first row, capped by its claim, 0 when non-deductible. Null when there is nothing to
     * split (no 규정금액, or no excess).
     */
    public static Plan plan(JsonNode row) {
        if (!row.path("ruledAmount").isNumber()) {
            return null;
        }
        double approval = row.path("approvalAmount").asDouble(0);
        double supply = row.path("supplyAmount").isNumber() ? row.path("supplyAmount").asDouble() : approval;
        double vat = row.path("vatAmount").asDouble(0);
        double used = "DAILY_COST".equals(row.path("tranKindType").asText("")) ? supply + vat : approval;
        double ruled = row.path("ruledAmount").asDouble();
        double excess = Math.max(0, used - ruled);
        if (excess <= 0) {
            return null;
        }
        double policyClaim = used - excess;
        double vatFirst = row.path("nonDeduction").asBoolean(false) ? 0 : Math.min(vat, policyClaim);
        return new Plan(used, ruled, policyClaim, excess, vatFirst);
    }

    /**
     * The divide request: the child set as their screen sends it (the captured USER split's shape
     * with the EXCESS markers of 06 §6.2.8). {@code reqAmt}, {@code approvalAmount} and
     * {@code ruledAmount} are deliberately absent; {@code issuedAmt} equals {@code splAmt}.
     */
    public ArrayNode rows(JsonNode row, JsonNode issuedDto, Plan plan, long excessAccountId,
                          String excessAccountName, JsonNode budgetDept) {
        ArrayNode out = objectMapper.createArrayNode();
        long policyAccount = row.path("accountSubjectId").asLong(0);
        out.add(child(row, issuedDto, 0, false, plan.policyClaim(), plan.vatFirst(),
                policyAccount > 0 ? policyAccount : null, row.path("accountSubjectName").asText(null), budgetDept));
        out.add(child(row, issuedDto, 1, true, plan.excess(), 0, excessAccountId, excessAccountName, budgetDept));
        return out;
    }

    private ObjectNode child(JsonNode row, JsonNode issuedDto, int order, boolean excessOver, double amount,
                             double vat, Long accountId, String accountName, JsonNode budgetDept) {
        ObjectNode c = objectMapper.createObjectNode();
        c.put("divisionOrder", order);
        c.put("divisionType", "EXCESS");
        c.put("excessOver", excessOver);
        c.put("issuedAmt", amount);
        c.put("splAmt", amount);
        c.put("vatAmt", vat);
        c.put("tranKindId", row.path("tranKindId").asLong(issuedDto.path("tranKindId").asLong(0)));
        c.put("tranKindName", issuedDto.path("tranKindName").asText(null));
        c.putNull("tranKindErpCode");
        c.putArray("issuedItems");
        c.putNull("corporationUser");
        ObjectNode slip = c.putObject("slip");
        slip.put("slipSplAmt", amount);
        slip.put("slipVatAmt", vat);
        if (accountId != null && accountId > 0) {
            slip.put("accountSubjectId", accountId);
            if (accountName != null) {
                slip.put("accountSubjectName", accountName);
            }
        }
        JsonNode dept = budgetDept;
        if (dept == null || !dept.hasNonNull("id") && !dept.hasNonNull("budgetDepartmentId")) {
            dept = null;
        }
        if (row.path("budgetDepartmentId").asLong(0) > 0) {
            slip.put("budgetDepartmentId", row.path("budgetDepartmentId").asLong());
            slip.put("budgetDepartmentName", row.path("budgetDepartmentName").asText(null));
            slip.put("budgetDepartmentErpCode", row.path("budgetDepartmentErpCode").asText(null));
        } else if (dept != null) {
            slip.put("budgetDepartmentId", dept.path("id").asLong(dept.path("budgetDepartmentId").asLong(0)));
            slip.put("budgetDepartmentName", dept.path("name").asText(dept.path("budgetDepartmentName").asText(null)));
            slip.put("budgetDepartmentErpCode", dept.path("erpCode").asText(dept.path("budgetDepartmentErpCode").asText(null)));
        }
        return c;
    }

    /**
     * Divide the receipt and read the children back: the active {@code EXCESS} issued rows of
     * {@code GET /api/v2/receipt/{receiptId}}, in {@code divisionOrder}. Throws when BizPlay
     * refuses the divide; returns an empty list when the read-back shows no children.
     */
    public List<JsonNode> divide(long receiptId, ArrayNode rows, String token) {
        String answer = bizplayGatewayService.divideReceipt(receiptId, rows, token);
        log.info("[SPLIT] receipt {} divided: {}", receiptId, answer);
        JsonNode receipt = bizplayGatewayService.getReceiptById(receiptId, token);
        List<JsonNode> children = new ArrayList<>();
        for (JsonNode ir : receipt.path("issuedReceipts")) {
            if (ir.path("isActive").asBoolean(true) && "EXCESS".equals(ir.path("divisionType").asText(""))) {
                children.add(ir);
            }
        }
        children.sort((a, b) -> Integer.compare(a.path("divisionOrder").asInt(0), b.path("divisionOrder").asInt(0)));
        return children;
    }
}
