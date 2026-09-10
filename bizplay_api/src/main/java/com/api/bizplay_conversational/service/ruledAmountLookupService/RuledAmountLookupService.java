package com.api.bizplay_conversational.service.ruledAmountLookupService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 규정금액 layer ① — the raw-data lookup, as BizPlay's own screen does it
 * ({@code 증빙 규정금액 산출/01_원자료조회_계약.md}, {@code 04_용도별_산출경로.md} §4.10).
 *
 * <p>One receipt's 규정 is not one call. The provider answers per (급지 section × user):
 * every 출장기간 section of the plan has its own region and dates, and for meals every companion
 * has their own limit. This service makes those calls against
 * {@code POST /api/v2/bstr/policy/renewal/limit}, with the full request contract (trip facts,
 * 예외 항목 ids, calcInputs), widens the dates by the 용도's allowed days, and merges the answers:
 * sections by per-day maximum, users by per-day sum.
 *
 * <p>What comes back is BASE amounts per day plus the matched conditions. Applying the conditions
 * (layer ②) is a separate step; the result carries everything it needs.
 */
public interface RuledAmountLookupService {

    /**
     * KRW per ONE unit of a currency (unit already folded in - JPY is quoted per 100 by the
     * provider), {@code Double.NaN} when no rate is known. 1 for KRW. The receipt's own pair
     * (approvalAmount / overseasApprovalAmount) is the rate that applied to it; anything else
     * comes from the provider's daily 환율.
     */
    @FunctionalInterface
    interface KrwRate {
        double perUnit(String currencyCode);
    }

    /**
     * @param doc           the settlement document being built (purpose, segment, type, dates,
     *                      issuedItems with the plan's period sections and 예외 항목, routes)
     * @param receipt       the receipt line (tranKindId, tranKindType, receiptEtc with vehicle,
     *                      food division, usage dates)
     * @param planPaper     the plan form definition (its EXPENSE_BEYOND items carry the allowed
     *                      days per 용도); may be null
     * @param corporationId the corporation
     * @param corpUserIds   who to ask for — the drafter, plus companions for meals
     * @param token         bearer for BizPlay
     * @return the merged answer, or null when there is no 규정 for this 용도 / nothing to ask with.
     *         Fields: {@code id, limitAmount, limitAmounts{date:base}, dayTypeMap, currencyCode,
     *         bstrPayClassType, bstrPayOptionType, bstrCategoryType, payType, calcEnabled,
     *         calcPeriod, appliedConditions[], sections[{regionId,start,end,corpUserId,response}]}
     */
    ObjectNode lookup(JsonNode doc, JsonNode receipt, JsonNode planPaper, long corporationId,
                      List<Long> corpUserIds, String token);

    /**
     * Layers ① AND ②: the lookup above, with the matched conditions applied per section by
     * {@link CalcConditionEngine} on the absolute period axis of that section's request. The
     * answer's {@code limitAmounts} is then the FINAL per-day amount in the 규정's own currency;
     * {@code limitAmountsKrw} the final amount in KRW (02 §6: converted first, then applied),
     * present when every needed rate was known - {@code rates} supplies them; {@code calcBreakdown}
     * the calculation detail ({@code baseAmount, items[{label,effect}], totalAmount});
     * {@code baseLimitAmounts} the untouched base; {@code calcDeferred} true when conditions
     * matched but the KRW figure could not be built.
     *
     * @param rates KRW per unit for the 규정's / the operands' currencies; null means "no rates"
     */
    ObjectNode lookup(JsonNode doc, JsonNode receipt, JsonNode planPaper, long corporationId,
                      List<Long> corpUserIds, String token, KrwRate rates);
}
