package com.api.bizplay_conversational.service.claimAmountService;

import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 규정금액 layer ③, first half (증빙 규정금액 산출/06_검증 §6.1, §6.3, §6.4, §6.5.7): what a receipt
 * line CLAIMS once its 규정금액 is known.
 * <ul>
 *   <li>the excess = {@code MAX(0, used − ruled)} on the USED-amount axis, never the claim (§6.1);</li>
 *   <li>the claim amount {@code reqAmt} auto-filled the way BizPlay's screen does it
 *       ({@code calculateDefaultReqAmt}, §6.4) from the corp's 신청금액 setting, then capped by
 *       the pay class ({@code getNonDivisionMax}, §6.3.2) - LIMITED to MIN(규정, 지출), FIXED /
 *       FUEL to the 규정금액, 실비 kinds and corporate cards to the spend, 기타증빙 to MIN(규정,
 *       지출) even without a rule;</li>
 *   <li>whether an 초과사유 is required (⑫, §6.5.7): only for 용도 with an ACTIVE excess-reason
 *       setting, by that setting's limit type.</li>
 * </ul>
 * Split receipts (USER / BASIC / EXCESS rows) are out of scope here - the agent never splits;
 * the excess split is the second half of layer ③. Running totals from OTHER expense reports on
 * the same plan ({@code alreadyUsedReqAmt}) are taken as 0 for now.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimAmountService {

    private static final Set<String> ACTUAL_LIKE = Set.of("ACTUAL", "ACTUAL_FIXED");
    private static final Set<String> RESERVATION_TYPES = Set.of("FLIGHT", "TRANS", "ACCOM");
    private static final Set<String> RESERVATION_CARDS = Set.of("CORP", "BZP_POINT");
    /** §6.3.4 - the card type on a receipt vs the expenseType key of the 신청금액 setting. */
    private static final Map<String, String> CARD_TO_EXPENSE_TYPE = Map.of(
            "CORP", "CORP_CARD", "CHECK", "CHECK", "PERSONAL", "PERSONAL_CARD", "ETC", "ETC_CARD",
            "ZERO", "ZERO_PAY", "BZP_POINT", "BZP_POINT", "BZP_MONEY", "BZP_MONEY");

    private final BizplayGatewayService bizplayGatewayService;

    /**
     * @param reqAmt         the claim amount to write, or null = "leave it to the user" (§6.4:
     *                       a 기타증빙 with no rule looked up)
     * @param cap            the most the claim may be (§6.3.2); {@code +∞} when unlimited
     * @param used           the used amount the excess is measured on (§6.1.3)
     * @param excess         {@code MAX(0, used − ruled)}, or null when there is no 규정금액 at all
     * @param standardField  the setting's basis that decided reqAmt (APPROVAL_AMOUNT / SUPPLY_AMOUNT
     *                       / RULED_AMOUNT / EMPTY), or the branch that short-circuited it
     * @param reasonRequired ⑫ - the receipt is over its 규정 by the active setting's rule and
     *                       carries no 초과사유 yet
     * @param limitType      the active setting's limit type (ONE_DAY / ALL), "" when none
     */
    public record Claim(Double reqAmt, double cap, double used, Double excess, String standardField,
                        boolean reasonRequired, String limitType) {
        public boolean capped() {
            return reqAmt != null && reqAmt < used;
        }
    }

    /** The corp's 신청금액 setting (§6.4.7), cached by the gateway; null when it cannot be read. */
    public JsonNode requestedAmountSetting(String token) {
        try {
            return bizplayGatewayService.getRequestedAmountSetting(token);
        } catch (RuntimeException e) {
            log.warn("[CLAIM] 신청금액 setting unavailable: {}", e.getMessage());
            return null;
        }
    }

    /** The 초과사유 settings per 용도 (§6.5.7), cached; null when the lookup fails (⑫ is then skipped). */
    public JsonNode exceedReasonSettings(String token) {
        try {
            return bizplayGatewayService.getExpenseExceedReasons(token);
        } catch (RuntimeException e) {
            log.warn("[CLAIM] 초과사유 settings unavailable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * The claim for one unsplit receipt line. {@code line} carries the row's own fields:
     * approvalAmount, supplyAmount, vatAmount, ruledAmount, overseasRuledAmount,
     * overseasApprovalAmount, currencyCode, cardType (or bstrReceiptType), tranKindType,
     * tranKindId, bstrPayClassType, bstrCategoryType, nonDeduction, additionalReceiptType,
     * excessReason.
     */
    public static Claim compute(JsonNode line, JsonNode setting, JsonNode exceedSettings) {
        double approval = line.path("approvalAmount").asDouble(0);
        double supply = line.path("supplyAmount").isNumber() ? line.path("supplyAmount").asDouble() : approval;
        double vat = line.path("vatAmount").asDouble(0);
        Double ruled = line.path("ruledAmount").isNumber() ? line.path("ruledAmount").asDouble() : null;
        double ruledOrZero = ruled == null ? 0 : ruled;
        double overseasRuled = line.path("overseasRuledAmount").asDouble(0);
        double foreign = line.path("overseasApprovalAmount").asDouble(line.path("foreignAmount").asDouble(0));
        String currency = line.path("currencyCode").asText("KRW").trim().toUpperCase(Locale.ROOT);
        String cardType = firstText(line, "cardType", "bstrReceiptType").toUpperCase(Locale.ROOT);
        String tranKindType = line.path("tranKindType").asText("");
        String payClass = line.path("bstrPayClassType").asText("");
        boolean nonDeduct = line.path("nonDeduction").asBoolean(false);

        // §6.1.3 the used amount - per diem has no approvalAmount after a save
        double used = "DAILY_COST".equals(tranKindType) ? supply + vat : approval;
        Double excess = ruled == null ? null : Math.max(0, used - ruled);

        boolean etc = "ETC".equals(cardType);
        boolean corp = "CORP".equals(cardType);
        boolean actualLike = ACTUAL_LIKE.contains(payClass);
        boolean foreignReceipt = !currency.isBlank() && !"KRW".equals(currency);
        boolean compareInForeign = foreignReceipt && overseasRuled > 0;          // §6.3.2 row 1.5
        boolean prepaid = RESERVATION_TYPES.contains(line.path("additionalReceiptType").asText(""))
                && RESERVATION_CARDS.contains(cardType);                             // §6.4.6

        // §6.3.2 getNonDivisionMax - in the code's order: CORP → FOOD → foreign → 실비 → pay class
        double cap;
        if (corp) {
            cap = approval;
        } else if ("FOOD".equals(tranKindType)) {
            cap = approval;
        } else if (compareInForeign) {
            cap = foreign <= overseasRuled ? approval : ruledOrZero;
        } else if (actualLike) {
            cap = approval;
        } else {
            cap = switch (payClass) {
                case "LIMITED" -> Math.min(ruledOrZero, approval);
                case "FIXED", "FUEL" -> ruledOrZero;
                default -> etc ? Math.min(ruledOrZero, approval) : Double.POSITIVE_INFINITY;   // row 7.5
            };
        }

        // §6.4.2 calculateDefaultReqAmt, unsplit path, in execution order
        Double reqAmt;
        String field;
        if (actualLike) {
            reqAmt = approval;
            field = "ACTUAL";
        } else if ("GRADE".equals(line.path("bstrCategoryType").asText(""))) {
            reqAmt = approval;
            field = "GRADE";
        } else if (compareInForeign) {
            reqAmt = foreign <= overseasRuled || prepaid ? approval : ruledOrZero;
            field = "FOREIGN";
        } else if ("FOOD".equals(tranKindType)) {
            reqAmt = approval;                                                       // alreadyUsed = 0
            field = "FOOD";
        } else if (setting == null || !setting.isObject()) {
            reqAmt = 0d;
            field = "NO_SETTING";
        } else if (!setting.path("requestedAmountUsed").asBoolean(false)) {
            reqAmt = 0d;
            field = "NOT_USED";
        } else if (payClass.isBlank() && ruledOrZero <= 0 && etc) {
            reqAmt = null;                                                           // §6.4.6: no rule, no auto-fill
            field = "MANUAL";
        } else {
            field = standardField(setting, cardType);
            double remainingRuled = (ruledOrZero > 0 || etc) ? Math.max(0, ruledOrZero) : Double.POSITIVE_INFINITY;
            boolean capToRuled = !prepaid && etc;
            reqAmt = switch (field) {
                case "APPROVAL_AMOUNT" -> capToRuled ? Math.min(approval, remainingRuled) : approval;
                case "SUPPLY_AMOUNT" -> {
                    double base = nonDeduct ? approval : supply;
                    yield capToRuled ? Math.min(base, remainingRuled) : base;
                }
                case "RULED_AMOUNT" -> prepaid ? approval : Math.min(remainingRuled, approval);
                default -> 0d;                                                       // EMPTY
            };
        }
        if (reqAmt != null && reqAmt > cap) {
            reqAmt = cap;                                                            // §6.3.3 isAllowedReqAmt
        }

        // ⑫ §6.5.7 - only a 용도 with an ACTIVE setting is judged, by that setting's limit type
        String limitType = "";
        boolean reasonRequired = false;
        long tranKindId = line.path("tranKindId").asLong(0);
        if (exceedSettings != null && exceedSettings.isArray() && tranKindId > 0 && !prepaid
                && !line.path("excessOver").asBoolean(false)) {
            for (JsonNode s : exceedSettings) {
                if (s.path("activated").asBoolean(false) && s.path("tranKindId").asLong(-1) == tranKindId) {
                    limitType = s.path("bstrLimitType").asText("");
                    break;
                }
            }
            if (!limitType.isEmpty()) {
                double claim = reqAmt != null ? reqAmt : line.path("reqAmt").asDouble(approval);
                boolean over;
                if (actualLike) {
                    over = approval < claim;
                } else if ("ONE_DAY".equals(limitType)) {
                    if ("FOOD".equals(tranKindType)) {
                        over = claim > ruledOrZero;              // one receipt's share of the day's meals
                    } else {
                        over = foreignReceipt ? (overseasRuled > 0 && foreign > overseasRuled) : ruledOrZero < claim;
                    }
                } else if ("ALL".equals(limitType)) {
                    // The 규정금액 on our line is already the per-day rules summed over the
                    // receipt's days (layer ②), so one receipt compares against its own total.
                    over = foreignReceipt && overseasRuled > 0 ? foreign > overseasRuled : claim > ruledOrZero;
                } else {
                    over = false;
                }
                reasonRequired = over && line.path("excessReason").asText("").isBlank();
            }
        }
        return new Claim(reqAmt, cap, used, excess, field, reasonRequired, limitType);
    }

    /** §6.4.5 resolveStandardField: the basis for this card type, EMPTY when nothing matches. */
    static String standardField(JsonNode setting, String cardType) {
        String expenseType = CARD_TO_EXPENSE_TYPE.getOrDefault(cardType, cardType);
        for (JsonNode item : setting.path("requestedAmountTypeList")) {
            if (expenseType.equals(item.path("expenseType").asText(""))) {
                return item.path("requestedAmountDefaultType").asText("EMPTY");
            }
        }
        return "EMPTY";
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String k : keys) {
            String v = node.path(k).asText("");
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
