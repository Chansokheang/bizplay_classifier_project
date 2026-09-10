package com.api.bizplay_conversational;

import com.api.bizplay_conversational.service.claimAmountService.ClaimAmountService;
import com.api.bizplay_conversational.service.claimAmountService.ClaimAmountService.Claim;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 규정금액 layer ③ (06_검증 §6.1 / §6.3 / §6.4 / §6.5.7) on the dev tenant's own 신청금액 setting
 * (ETC_CARD → APPROVAL_AMOUNT, CORP_CARD → EMPTY) plus a synthetic 초과사유 setting, since the
 * tenant has none registered.
 */
class ClaimAmountServiceTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JsonNode setting() throws Exception {
        return M.readTree("{\"requestedAmountUsed\":true,\"requestedAmountTypeList\":["
                + "{\"expenseType\":\"ETC_CARD\",\"requestedAmountDefaultType\":\"APPROVAL_AMOUNT\"},"
                + "{\"expenseType\":\"CORP_CARD\",\"requestedAmountDefaultType\":\"EMPTY\"},"
                + "{\"expenseType\":\"PERSONAL_CARD\",\"requestedAmountDefaultType\":\"RULED_AMOUNT\"}]}");
    }

    private static JsonNode exceed(String limitType, boolean activated) throws Exception {
        return M.readTree("[{\"id\":1,\"tranKindId\":11719,\"tranKindName\":\"교통비\",\"bstrLimitType\":\""
                + limitType + "\",\"activated\":" + activated + "}]");
    }

    private static ObjectNode line(String cardType, String payClass, double approval, double ruled) {
        ObjectNode l = M.createObjectNode();
        l.put("cardType", cardType);
        l.put("bstrPayClassType", payClass);
        l.put("approvalAmount", approval);
        l.put("supplyAmount", approval);
        l.put("vatAmount", 0);
        l.put("ruledAmount", ruled);
        l.put("tranKindId", 11719);
        l.put("tranKindType", "TRANSPORT");
        l.put("currencyCode", "KRW");
        l.put("excessReason", "");
        return l;
    }

    @Test
    void fixedRuleCapsTheClaimAndMeasuresTheExcessOnTheSpend() throws Exception {
        Claim c = ClaimAmountService.compute(line("ETC", "FIXED", 60000, 30000), setting(), null);
        assertEquals(30000, c.reqAmt());                 // §6.3.2 row 5: FIXED → 규정금액
        assertEquals(30000, c.cap());
        assertEquals(30000, c.excess());                 // §6.1: MAX(0, 60,000 − 30,000)
        assertTrue(c.capped());
        assertFalse(c.reasonRequired());                 // no 초과사유 setting → never required
    }

    @Test
    void limitedRuleClaimsTheSmallerOfRuleAndSpend() throws Exception {
        assertEquals(30000, ClaimAmountService.compute(line("ETC", "LIMITED", 60000, 30000), setting(), null).reqAmt());
        assertEquals(20000, ClaimAmountService.compute(line("ETC", "LIMITED", 20000, 30000), setting(), null).reqAmt());
    }

    @Test
    void actualCostClaimsTheSpendWhateverTheRule() throws Exception {
        Claim c = ClaimAmountService.compute(line("ETC", "ACTUAL", 60000, 30000), setting(), null);
        assertEquals(60000, c.reqAmt());
        assertEquals("ACTUAL", c.standardField());
        assertEquals(60000, ClaimAmountService.compute(line("ETC", "ACTUAL_FIXED", 60000, 0), setting(), null).reqAmt());
    }

    @Test
    void miscReceiptWithNoRuleIsLeftToTheUser() throws Exception {
        Claim c = ClaimAmountService.compute(line("ETC", "", 60000, 0), setting(), null);
        assertNull(c.reqAmt());                          // §6.4.6: no auto-fill for 기타증빙 without a rule
        assertEquals(0, c.cap());                        // §6.3.2 row 7.5: a 규정금액 of 0 is a limit of 0
    }

    @Test
    void corporateCardFollowsItsOwnBasisAndCap() throws Exception {
        Claim c = ClaimAmountService.compute(line("CORP", "LIMITED", 60000, 30000), setting(), null);
        assertEquals(0, c.reqAmt());                     // CORP_CARD basis EMPTY → 0 on this tenant
        assertEquals(60000, c.cap());                    // §6.3.2 row 1: a corporate card caps at the spend
    }

    @Test
    void personalCardOnTheRuledBasisClaimsTheRule() throws Exception {
        assertEquals(30000, ClaimAmountService.compute(line("PERSONAL", "LIMITED", 60000, 30000), setting(), null).reqAmt());
    }

    @Test
    void foreignRuleWithinItsOwnCurrencyClaimsTheFullKrwSpend() throws Exception {
        ObjectNode l = line("ETC", "LIMITED", 267580, 287580);
        l.put("currencyCode", "USD");
        l.put("overseasApprovalAmount", 200);
        l.put("overseasRuledAmount", 215);
        Claim c = ClaimAmountService.compute(l, setting(), null);
        assertEquals(267580, c.reqAmt());                // §6.4.3: foreign 200 ≤ 215 → the KRW spend
        assertEquals("FOREIGN", c.standardField());
        l.put("overseasRuledAmount", 100);
        assertEquals(287580, ClaimAmountService.compute(l, setting(), null).reqAmt());   // over → the KRW 규정금액
    }

    @Test
    void excessReasonIsRequiredOnlyByAnActiveSettingAndOnlyWhileMissing() throws Exception {
        ObjectNode over = line("ETC", "LIMITED", 60000, 30000);
        assertFalse(ClaimAmountService.compute(over, setting(), exceed("ONE_DAY", false)).reasonRequired());
        // LIMITED caps the claim to the rule, so on the claim axis ONE_DAY is not over (§6.1.2 says so)
        assertFalse(ClaimAmountService.compute(over, setting(), exceed("ONE_DAY", true)).reasonRequired());
        // ACTUAL: the claim equals the spend, and 60,000 spent on a 30,000 rule is judged on ruled < claim? no -
        // ACTUAL compares approval < claim (§6.5.7), which never holds for an auto-filled claim
        assertFalse(ClaimAmountService.compute(line("ETC", "ACTUAL", 60000, 30000), setting(), exceed("ONE_DAY", true)).reasonRequired());
        // A personal card on the APPROVAL basis is not capped by the rule → ruled < claim → reason due
        JsonNode approvalBasis = M.readTree("{\"requestedAmountUsed\":true,\"requestedAmountTypeList\":["
                + "{\"expenseType\":\"PERSONAL_CARD\",\"requestedAmountDefaultType\":\"APPROVAL_AMOUNT\"}]}");
        ObjectNode personal = line("PERSONAL", "", 60000, 30000);
        Claim due = ClaimAmountService.compute(personal, approvalBasis, exceed("ONE_DAY", true));
        assertEquals(60000, due.reqAmt());
        assertTrue(due.reasonRequired());
        assertEquals("ONE_DAY", due.limitType());
        personal.put("excessReason", "성수기라 비쌌음");
        assertFalse(ClaimAmountService.compute(personal, approvalBasis, exceed("ONE_DAY", true)).reasonRequired());
    }

    @Test
    void settingUnavailableOrOffClaimsZero() throws Exception {
        assertEquals(0, ClaimAmountService.compute(line("ETC", "LIMITED", 60000, 30000), null, null).reqAmt());
        JsonNode off = M.readTree("{\"requestedAmountUsed\":false,\"requestedAmountTypeList\":[]}");
        assertEquals(0, ClaimAmountService.compute(line("ETC", "LIMITED", 60000, 30000), off, null).reqAmt());
    }
}
