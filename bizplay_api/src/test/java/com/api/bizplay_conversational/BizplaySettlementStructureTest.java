package com.api.bizplay_conversational;

import com.api.bizplay_conversational.config.BizplayProperties;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.repository.ConversationalAgentSessionRepo;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.api.bizplay_conversational.service.bizplaySettlementAgentService.BizplaySettlementAgentServiceImple;
import com.api.bizplay_conversational.service.formFollowUpAgentService.FormFollowUpAgentService;
import com.api.bizplay_conversational.service.formSkeletonService.FormSkeletonServiceImple;
import com.api.bizplay_conversational.service.guardrailAgentService.GuardrailAgentService;
import com.api.bizplay_conversational.service.planPickerAgentService.PlanPickerAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives {@link BizplaySettlementAgentServiceImple} through its whole stage machine with stubbed
 * BizPlay gateway responses, then structurally diffs the produced settlement draft against the
 * captured 정산서 request-body sample
 * ({@code 04. Document/API REQ/ExpenseReport/정산서_requestBody_샘플 (1).json}).
 *
 * <p>Nothing here talks to the network: the gateway (④ plan list, ⑤ plan detail, ⑥ receipt stream)
 * and the paper definition are fixtures shaped exactly like the provider's real payloads, so the
 * only thing under test is the agent's own document construction.
 */
class BizplaySettlementStructureTest {

    private static final Path SAMPLE = Path.of("src", "main", "resources", "docs",
            "정산서_requestBody_샘플 (1).json");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void settlementDraftMatchesTheCapturedSampleStructure() throws Exception {
        JsonNode sample = mapper.readTree(Files.readString(SAMPLE)).get(0);

        // --- collaborators ---------------------------------------------------------
        InMemorySessionRepo sessions = new InMemorySessionRepo();

        GuardrailAgentService guardrail = mock(GuardrailAgentService.class);
        when(guardrail.check(anyString())).thenReturn(GuardrailAgentService.GuardrailResult.ok());

        PlanPickerAgentService planPicker = mock(PlanPickerAgentService.class);
        when(planPicker.pick(anyString(), any())).thenReturn(null);

        FormFollowUpAgentService followUp = mock(FormFollowUpAgentService.class);
        when(followUp.composeFollowUp(any(), any(), anyBoolean())).thenReturn("(follow-up)");
        when(followUp.answerDraftQuestion(anyString(), anyString(), anyBoolean())).thenReturn("(answer)");

        BizplayGatewayService gateway = mock(BizplayGatewayService.class);
        when(gateway.getPlanList(anyLong(), anyString(), anyString(), any())).thenReturn(planList());
        when(gateway.getPlanDetail(eq(9001L), any())).thenReturn(planDetail());
        when(gateway.getPapers(eq(102L), eq(52L), any())).thenReturn(papers());
        when(gateway.getUnattachedReceipts(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(receiptStream());

        BizplayProperties props = new BizplayProperties();
        props.setDefaultCorpUserId("161");

        BizplaySettlementAgentServiceImple agent = new BizplaySettlementAgentServiceImple(
                sessions, guardrail, gateway, new FormSkeletonServiceImple(mapper),
                planPicker, followUp, props, mapper);

        // --- drive the flow --------------------------------------------------------
        String sessionId = null;
        String[][] turns = {
                {"2026-08-01 ~ 2026-08-31", "plan search (④)"},
                {"settle-plan:9001", "plan import (⑤)"},
                {"evidence-period:default", "evidence period"},
                {"card-types:CORP,PERSONAL", "receipt lookup (⑥)"},
                {"receipt:all", "attach receipts"},
                {"receipts-done", "finish"},
        };
        System.out.println("================ SETTLEMENT AGENT RUN ================");
        for (String[] turn : turns) {
            BizplayPlanAgentRequest req = new BizplayPlanAgentRequest();
            req.setCorpNo("1234567890");
            req.setCorpUserId("161");
            req.setSessionId(sessionId);
            req.setMessage(turn[0]);
            BizplayPlanAgentResponse res = agent.chat(req, "test-token");
            sessionId = res.getSessionId();
            System.out.printf("%-26s -> intent=%-22s subAgents=%s%n    reply: %s%n",
                    turn[1], res.getIntent(), res.getSubAgents(), res.getReply());
        }

        JsonNode produced = sessions.get(UUID.fromString(sessionId)).getDraftJson().get(0);

        System.out.println("\n================ PRODUCED DOCUMENT ================");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(produced));

        // --- structural diff -------------------------------------------------------
        List<String> missing = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        List<String> typeMismatch = new ArrayList<>();
        diff("", sample, produced, missing, extra, typeMismatch);

        System.out.println("\n================ STRUCTURAL DIFF vs SAMPLE ================");
        report("MISSING (in sample, absent from the agent's output)", missing);
        report("EXTRA (produced but not in the sample)", extra);
        report("TYPE MISMATCH", typeMismatch);

        System.out.println("\n---- top-level key order ----");
        System.out.println("sample  : " + names(sample));
        System.out.println("produced: " + names(produced));

        System.out.println("\n---- amount fields ----");
        for (String k : new String[]{"totalBstrAmount", "totalPointAmount", "totalPersonalAmount",
                "totalSettleAmount", "dailyCostAmount", "lodgingCostAmount", "fuelCostAmount",
                "foodCostAmount", "incidentalCostAmount", "publicFixCostAmount"}) {
            System.out.printf("  %-22s sample=%-10s produced=%s%n", k,
                    sample.path(k).asText("-"), produced.path(k).asText("(absent)"));
        }

        System.out.println("\n---- array cardinality ----");
        for (String k : new String[]{"issuedItems", "approvalLines", "referenceLines", "receiptIds",
                "issuedFields", "etcReceiptSaveRequests", "bstrReceipts", "originDocuments", "files"}) {
            System.out.printf("  %-24s sample=%-3d produced=%s%n", k, sample.path(k).size(),
                    produced.has(k) ? String.valueOf(produced.path(k).size()) : "(absent)");
        }
        System.out.println("\n==========================================================");
    }

    // --- diff helpers ------------------------------------------------------------

    /** Recursive key/shape diff. Arrays compare element 0 (shape), not cardinality. */
    private void diff(String path, JsonNode expected, JsonNode actual,
                      List<String> missing, List<String> extra, List<String> typeMismatch) {
        if (expected.isObject()) {
            if (!actual.isObject()) {
                typeMismatch.add(path + " : sample=object, produced=" + kind(actual));
                return;
            }
            LinkedHashSet<String> expectedKeys = new LinkedHashSet<>();
            expected.fieldNames().forEachRemaining(expectedKeys::add);
            LinkedHashSet<String> actualKeys = new LinkedHashSet<>();
            actual.fieldNames().forEachRemaining(actualKeys::add);
            for (String k : expectedKeys) {
                String child = path.isEmpty() ? k : path + "." + k;
                if (!actualKeys.contains(k)) {
                    missing.add(child + "   (sample: " + preview(expected.get(k)) + ")");
                } else {
                    diff(child, expected.get(k), actual.get(k), missing, extra, typeMismatch);
                }
            }
            for (String k : actualKeys) {
                if (!expectedKeys.contains(k)) {
                    extra.add((path.isEmpty() ? k : path + "." + k)
                            + "   (produced: " + preview(actual.get(k)) + ")");
                }
            }
        } else if (expected.isArray()) {
            if (!actual.isArray()) {
                typeMismatch.add(path + " : sample=array, produced=" + kind(actual));
                return;
            }
            if (!expected.isEmpty() && !actual.isEmpty()) {
                diff(path + "[]", expected.get(0), actual.get(0), missing, extra, typeMismatch);
            }
        } else if (!expected.isNull() && !actual.isNull()
                && expected.getNodeType() != actual.getNodeType()) {
            typeMismatch.add(path + " : sample=" + kind(expected) + " (" + preview(expected)
                    + "), produced=" + kind(actual) + " (" + preview(actual) + ")");
        }
    }

    private static void report(String title, List<String> items) {
        System.out.println("\n" + title + " — " + items.size());
        if (items.isEmpty()) {
            System.out.println("  (none)");
        }
        items.forEach(i -> System.out.println("  - " + i));
    }

    private static String kind(JsonNode n) {
        return n.getNodeType().toString().toLowerCase();
    }

    private static String preview(JsonNode n) {
        String s = n.toString();
        return s.length() <= 70 ? s : s.substring(0, 70) + "…";
    }

    private static String names(JsonNode n) {
        List<String> keys = new ArrayList<>();
        n.fieldNames().forEachRemaining(keys::add);
        return String.join(", ", keys);
    }

    // --- fixtures (shaped like the provider's real payloads) ---------------------

    private JsonNode papers() throws Exception {
        return mapper.readTree("""
                [{
                  "id": 611,
                  "name": "출장정산서",
                  "bstrType": "DOMESTIC",
                  "paperKind": {"paperKindType": "EXPENSE_REPORT"},
                  "bstrDestinationUsed": true,
                  "paperItemOrderDto": [
                    {"paperItemType":"BASIC_ITEM","basicItemType":"BASIC_TITLE","used":true,"required":true},
                    {"paperItemType":"BASIC_ITEM","basicItemType":"BASIC_CONTENT","used":true,"required":false},
                    {"paperItemType":"ITEM","used":true,"required":true,"travelerItemUsed":false,
                     "itemDto":{"id":207,"itemType":"BSTR_PERIOD","name":"출장 기간","itemList":[],"labelItems":[]}},
                    {"paperItemType":"ITEM","used":true,"required":true,"travelerItemUsed":false,
                     "itemDto":{"id":214,"itemType":"COST_CENTER","name":"예산부서",
                       "itemList":[{"id":3021,"name":"개발2팀","erpCode":"NJ072"}],"labelItems":[]}},
                    {"paperItemType":"ITEM","used":true,"required":false,"travelerItemUsed":false,
                     "itemDto":{"id":231,"itemType":"POSTING_DATE","name":"전기일","itemList":[],"labelItems":[]}},
                    {"paperItemType":"ITEM","used":true,"required":false,"travelerItemUsed":true,
                     "itemDto":{"id":240,"itemType":"DAILY_COST","name":"일비","requestWay":"PAY_DAY_CONFIRM",
                       "itemList":[],"labelItems":[]}},
                    {"paperItemType":"ITEM","used":true,"required":false,"travelerItemUsed":false,
                     "itemDto":{"id":252,"itemType":"TEXT","name":"출장 상세내용","itemList":[],"labelItems":[]}}
                  ]
                }]
                """);
    }

    private JsonNode planList() throws Exception {
        return mapper.readTree("""
                [{"approvalId":9001,"docNo":"BSTR-2026-0810","title":"부산 고객사 방문",
                  "bstrStartDate":"2026-08-10","bstrEndDate":"2026-08-12","bstrPurpose":"국내출장"}]
                """);
    }

    private JsonNode planDetail() throws Exception {
        return mapper.readTree("""
                {
                  "approvalId": 9001, "bstrId": 5001, "docNo": "BSTR-2026-0810",
                  "title": "부산 고객사 방문", "content": "부산 고객사 방문 출장 정산",
                  "draftUserName": "김민수", "draftUserId": 161,
                  "bstrPurposeId": 102, "bstrSegmentId": 52,
                  "bstrType": null, "bstrPayType": null,
                  "bstrStartDate": "2026-08-10", "bstrEndDate": "2026-08-12",
                  "issuedItems": [
                    {"item":{"id":214,"itemType":"COST_CENTER","name":"예산부서"},
                     "value":null,"value2":null,
                     "selections":[{"selectionId":3021,"selectionName":"개발2팀","selectionErpCode":"NJ072",
                                    "selectionMemo":null,"selectionAreaInfo":null,"selectionContent":"ignored"}]},
                    {"item":{"id":240,"itemType":"DAILY_COST","name":"일비"},
                     "value":"true","value2":null,"selections":[]},
                    {"item":{"id":252,"itemType":"TEXT","name":"출장 상세내용"},
                     "value":"부산 고객사 정기 점검 및 신규 요건 협의","value2":null,"selections":[]}
                  ]
                }
                """);
    }

    private JsonNode receiptStream() throws Exception {
        return mapper.readTree("""
                [
                  {
                    "id": 55101, "cardType": "CORP", "etcReceiptType": "RECEIPT",
                    "tranKindId": 1101, "tranKindType": "TRANSPORT", "mestName": "한국철도공사",
                    "approvalDate": "2026-08-10", "approvalTime": "081500", "approvalNumber": "30291884",
                    "approvalAmount": 47000, "approvalCanceled": false,
                    "supplyAmount": 42727, "vatAmount": 4273, "serviceCharge": 0,
                    "currencyCode": "KRW", "exchangeRate": null,
                    "maskCardNumber": "9410-****-****-1123",
                    "usedStartDate": "2026-08-10", "usedEndDate": "2026-08-10",
                    "deductionNonDeduction": "DEDUCTABLE", "ruledAmount": 0,
                    "overseasApprovalAmount": 0, "overseasRuledAmount": 0,
                    "depart": "서울", "arrival": "부산",
                    "imageIds": [88301],
                    "issuedReceiptDtos": [{
                      "id": 77201, "receiptId": 55101, "tranKindId": 1101, "tranKindType": "TRANSPORT",
                      "issuedAmt": 47000, "splAmt": 42727, "vatAmt": 4273, "requestAmount": 47000,
                      "slip": {
                        "slipAmt": 47000, "slipSplAmt": 42727, "slipVatAmt": 4273,
                        "budgetDepartmentId": 3021, "budgetDepartmentName": "개발2팀",
                        "budgetDepartmentErpCode": "NJ072",
                        "accountSubjectId": 812, "accountSubjectName": "여비교통비",
                        "accountSubjectErpCode": "51230",
                        "taxCodeId": 17, "taxName": "매입세액공제", "branchOfficeId": 5,
                        "internalOrderId": null, "wbsId": null, "projectId": null,
                        "projectName": null, "projectErpCode": null,
                        "summary": "KTX 서울-부산 왕복", "docDate": "2026-08-13"
                      }
                    }]
                  },
                  {
                    "id": 55102, "cardType": "PERSONAL", "etcReceiptType": "RECEIPT",
                    "tranKindId": 1302, "tranKindType": "FOOD", "mestName": "부산해물탕",
                    "approvalDate": "2026-08-11", "approvalTime": "123200", "approvalNumber": "77120043",
                    "approvalAmount": 24000, "approvalCanceled": false,
                    "supplyAmount": 21819, "vatAmount": 2181, "serviceCharge": 0,
                    "currencyCode": "KRW", "exchangeRate": null,
                    "maskCardNumber": "5310-****-****-8842",
                    "usedStartDate": "2026-08-11", "usedEndDate": "2026-08-11",
                    "deductionNonDeduction": "DEDUCTABLE", "ruledAmount": 30000,
                    "overseasApprovalAmount": 0, "overseasRuledAmount": 0,
                    "imageIds": [],
                    "issuedReceiptDtos": [{
                      "id": 77202, "receiptId": 55102, "tranKindId": 1302, "tranKindType": "FOOD",
                      "issuedAmt": 24000, "splAmt": 21819, "vatAmt": 2181, "requestAmount": 24000,
                      "slip": {
                        "slipAmt": 24000, "slipSplAmt": 21819, "slipVatAmt": 2181,
                        "budgetDepartmentId": 3021, "budgetDepartmentName": "개발2팀",
                        "budgetDepartmentErpCode": "NJ072",
                        "accountSubjectId": 815, "accountSubjectName": "복리후생비",
                        "accountSubjectErpCode": "51410",
                        "taxCodeId": 17, "taxName": "매입세액공제", "branchOfficeId": 5,
                        "internalOrderId": null, "wbsId": null, "projectId": null,
                        "projectName": null, "projectErpCode": null,
                        "summary": "출장 중 식대", "docDate": "2026-08-13"
                      }
                    }]
                  }
                ]
                """);
    }

    /** In-memory stand-in for the MyBatis mapper (its default save/findById stay real). */
    private static class InMemorySessionRepo implements ConversationalAgentSessionRepo {
        private final Map<UUID, ConversationalAgentSession> store = new HashMap<>();

        ConversationalAgentSession get(UUID id) {
            return store.get(id);
        }

        @Override
        public ConversationalAgentSession findOneById(UUID id) {
            return store.get(id);
        }

        @Override
        public int deleteById(UUID id) {
            return store.remove(id) == null ? 0 : 1;
        }

        @Override
        public List<ConversationalAgentSession> findByCorpNo(String corpNo) {
            return new ArrayList<>(store.values());
        }

        @Override
        public void insert(ConversationalAgentSession session) {
            store.put(session.getId(), session);
        }

        @Override
        public void update(ConversationalAgentSession session) {
            store.put(session.getId(), session);
        }
    }
}
