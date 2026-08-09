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
import com.api.bizplay_conversational.service.slotFillerAgentService.SlotFillerAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        SlotFillerAgentService slotFiller = mock(SlotFillerAgentService.class);
        when(slotFiller.extract(anyString(), any(), anyBoolean())).thenReturn(mapper.createObjectNode());

        FormFollowUpAgentService followUp = mock(FormFollowUpAgentService.class);
        when(followUp.composeFollowUp(any(), any(), anyBoolean())).thenReturn("(follow-up)");
        when(followUp.answerDraftQuestion(anyString(), anyString(), anyBoolean())).thenReturn("(answer)");

        BizplayGatewayService gateway = mock(BizplayGatewayService.class);
        when(gateway.getPlanList(anyLong(), anyString(), anyString(), any())).thenReturn(planList());
        when(gateway.getPlanDetail(eq(9001L), any())).thenReturn(planDetail());
        when(gateway.getPapers(eq(102L), eq(52L), any())).thenReturn(papers());
        when(gateway.getUnattachedReceipts(anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(receiptStream());

        BizplayProperties props = new BizplayProperties();
        props.setDefaultCorpUserId("161");

        BizplaySettlementAgentServiceImple agent = new BizplaySettlementAgentServiceImple(
                sessions, guardrail, gateway, new FormSkeletonServiceImple(mapper),
                planPicker, followUp, slotFiller, Runnable::run, props, mapper);

        // --- drive the flow --------------------------------------------------------
        String sessionId = null;
        String[][] turns = {
                {"2026-08-01 ~ 2026-08-31", "plan search (④)"},
                {"settle-plan:9001", "plan import (⑤)"},
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

    /**
     * The settlement submit (⑦) must POST the settlement-shaped draft to the gateway's OWN
     * {@code postSettlementDraft} (→ /bstr/report/draft) and NEVER to the plan draft path. Guards
     * the separation the user asked for: plan and settlement each keep their own body + endpoint.
     */
    @Test
    void createSettlementPostsSettlementBodyToSettlementEndpointNeverPlan() throws Exception {
        InMemorySessionRepo sessions = new InMemorySessionRepo();

        GuardrailAgentService guardrail = mock(GuardrailAgentService.class);
        when(guardrail.check(anyString())).thenReturn(GuardrailAgentService.GuardrailResult.ok());
        PlanPickerAgentService planPicker = mock(PlanPickerAgentService.class);
        when(planPicker.pick(anyString(), any())).thenReturn(null);
        SlotFillerAgentService slotFiller = mock(SlotFillerAgentService.class);
        when(slotFiller.extract(anyString(), any(), anyBoolean())).thenReturn(mapper.createObjectNode());
        FormFollowUpAgentService followUp = mock(FormFollowUpAgentService.class);

        BizplayGatewayService gateway = mock(BizplayGatewayService.class);
        when(gateway.getPlanList(anyLong(), anyString(), anyString(), any())).thenReturn(planList());
        when(gateway.getPlanDetail(eq(9001L), any())).thenReturn(planDetail());
        when(gateway.getPapers(eq(102L), eq(52L), any())).thenReturn(papers());
        when(gateway.getUnattachedReceipts(anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(receiptStream());
        when(gateway.postSettlementDraft(any(), anyString())).thenReturn("저장되었습니다.");

        BizplayProperties props = new BizplayProperties();
        props.setDefaultCorpUserId("161");

        BizplaySettlementAgentServiceImple agent = new BizplaySettlementAgentServiceImple(
                sessions, guardrail, gateway, new FormSkeletonServiceImple(mapper),
                planPicker, followUp, slotFiller, Runnable::run, props, mapper);

        String sessionId = null;
        for (String msg : new String[]{"2026-08-01 ~ 2026-08-31", "settle-plan:9001",
                "card-types:CORP,PERSONAL", "receipt:all", "receipts-done"}) {
            BizplayPlanAgentRequest req = new BizplayPlanAgentRequest();
            req.setCorpNo("1234567890");
            req.setCorpUserId("161");
            req.setSessionId(sessionId);
            req.setMessage(msg);
            sessionId = agent.chat(req, "test-token").getSessionId();
        }

        // Submit with one picked approver on top of the drafter's DRAFT line.
        JsonNode approvalLines = mapper.readTree("[{\"corporationUserId\":205,\"approvalKindType\":\"APPROVAL\"}]");
        BizplayPlanAgentResponse res = agent.createSettlement(sessionId, "1234567890", "test-token", approvalLines);

        // ⑦ went to the settlement endpoint, and the plan draft path was never touched.
        ArgumentCaptor<JsonNode> posted = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).postSettlementDraft(posted.capture(), eq("test-token"));
        verify(gateway, never()).postPlanDraft(any(), any());

        JsonNode doc = posted.getValue().get(0);
        // The posted body is the 정산서 shape (settlement-only keys), not a plan body.
        for (String settlementKey : new String[]{"receiptIds", "bstrReceipts", "issuedFields",
                "etcReceiptSaveRequests", "totalSettleAmount", "dailyCostAmount"}) {
            assertTrue(doc.has(settlementKey),
                    "posted settlement body is missing the settlement-only key: " + settlementKey);
        }
        // DRAFT line kept at order 0, the picked approver appended as APPROVAL at order 1.
        JsonNode lines = doc.path("approvalLines");
        assertEquals(2, lines.size(), "expected the DRAFT line plus one picked approver");
        assertEquals("DRAFT", lines.get(0).path("approvalKindType").asText());
        assertEquals("APPROVAL", lines.get(1).path("approvalKindType").asText());
        assertEquals(205L, lines.get(1).path("corporationUserId").asLong());
        assertEquals("CREATE_SETTLEMENT", res.getIntent());
        assertEquals("POSTED", res.getStatus());
        System.out.println("✓ settlement submit posted to /bstr/report/draft with body keys "
                + names(doc) + "; status=" + res.getStatus());
    }

    /**
     * The ④ plan pick feeds a TABLE in the chat UI, so every candidate must carry its columns as
     * structured meta (목적 · 제목 · 문서번호 · 기간 · 기안자 · 등록자) — and the list must actually
     * respect the period the user asked for, which the provider's own query does not.
     */
    @Test
    void planPickOffersPeriodFilteredCandidatesWithTableColumns() throws Exception {
        InMemorySessionRepo sessions = new InMemorySessionRepo();
        GuardrailAgentService guardrail = mock(GuardrailAgentService.class);
        when(guardrail.check(anyString())).thenReturn(GuardrailAgentService.GuardrailResult.ok());
        PlanPickerAgentService planPicker = mock(PlanPickerAgentService.class);
        when(planPicker.pick(anyString(), any())).thenReturn(null);
        SlotFillerAgentService slotFiller = mock(SlotFillerAgentService.class);
        when(slotFiller.extract(anyString(), any(), anyBoolean())).thenReturn(mapper.createObjectNode());
        FormFollowUpAgentService followUp = mock(FormFollowUpAgentService.class);

        BizplayGatewayService gateway = mock(BizplayGatewayService.class);
        when(gateway.getPlanList(anyLong(), anyString(), anyString(), any())).thenReturn(planList());

        BizplayProperties props = new BizplayProperties();
        props.setDefaultCorpUserId("161");
        BizplaySettlementAgentServiceImple agent = new BizplaySettlementAgentServiceImple(
                sessions, guardrail, gateway, new FormSkeletonServiceImple(mapper),
                planPicker, followUp, slotFiller, Runnable::run, props, mapper);

        BizplayPlanAgentRequest req = new BizplayPlanAgentRequest();
        req.setCorpNo("1234567890");
        req.setCorpUserId("161");
        req.setMessage("2026-08-01 ~ 2026-08-31");
        BizplayPlanAgentResponse res = agent.chat(req, "test-token");

        var group = res.getPendingChoices().get(0);
        assertEquals("PLAN", group.getKind());
        assertEquals(1, group.getOptions().size(),
                "only the August plan overlaps the asked period — the 2027 one must be filtered out");
        var meta = group.getOptions().get(0).getMeta();
        assertEquals("국내출장", meta.get("purpose"));
        assertEquals("부산 고객사 방문", meta.get("title"));
        assertEquals("BSTR-2026-0810", meta.get("docNo"));
        assertEquals("2026-08-10", meta.get("startDate"));
        assertEquals("2026-08-12", meta.get("endDate"));
        assertEquals("김민수", meta.get("drafter"));
        assertEquals("박서연", meta.get("registrar"));
        assertEquals("settle-plan:9001", group.getOptions().get(0).getSendText());

        // A period with no plan at all still offers the recent ones — but says so, rather than
        // claiming they fall inside the period.
        BizplayPlanAgentRequest empty = new BizplayPlanAgentRequest();
        empty.setCorpNo("1234567890");
        empty.setCorpUserId("161");
        empty.setMessage("2030-01-01 ~ 2030-01-31");
        BizplayPlanAgentResponse fallback = agent.chat(empty, "test-token");
        assertEquals(2, fallback.getPendingChoices().get(0).getOptions().size());
        assertTrue(fallback.getReply().contains("most recent"), "reply: " + fallback.getReply());
        // ISO timestamps from the provider are trimmed to days for display.
        assertEquals("2027-03-01",
                fallback.getPendingChoices().get(0).getOptions().get(1).getMeta().get("startDate"));
        System.out.println("✓ plan pick: period-filtered candidates with table columns "
                + meta + "; out-of-period fallback lists " + fallback.getPendingChoices().get(0)
                        .getOptions().size() + " recent plans");
    }

    /**
     * The slot layer's behavior: one rich message fills several slots at once (period + card type +
     * a plan hint), but the agent still LISTS the matching plans for the user to pick — it never
     * auto-selects one. After the user picks, the later card-type question is skipped because the
     * card type was already in hand. Proves "always let the user pick" + "data in hand → never ask again".
     */
    @Test
    void richFirstMessageFillsSlotsListsPlansThenSkipsCardQuestionAfterPick() throws Exception {
        InMemorySessionRepo sessions = new InMemorySessionRepo();

        GuardrailAgentService guardrail = mock(GuardrailAgentService.class);
        when(guardrail.check(anyString())).thenReturn(GuardrailAgentService.GuardrailResult.ok());
        PlanPickerAgentService planPicker = mock(PlanPickerAgentService.class);
        when(planPicker.pick(anyString(), any())).thenReturn(null);
        FormFollowUpAgentService followUp = mock(FormFollowUpAgentService.class);

        // The slot-filler pulls the period + card type + plan hint out of the single message.
        SlotFillerAgentService slotFiller = mock(SlotFillerAgentService.class);
        JsonNode extracted = mapper.readTree(
                "{\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\","
                        + "\"cardTypes\":[\"CORP\"],\"planHint\":\"Gwangju\"}");
        when(slotFiller.extract(anyString(), any(), anyBoolean())).thenReturn(extracted);

        BizplayGatewayService gateway = mock(BizplayGatewayService.class);
        when(gateway.getPlanList(anyLong(), anyString(), anyString(), any())).thenReturn(planList());
        when(gateway.getPlanDetail(eq(9001L), any())).thenReturn(planDetail());
        when(gateway.getPapers(eq(102L), eq(52L), any())).thenReturn(papers());
        when(gateway.getUnattachedReceipts(anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(receiptStream());

        BizplayProperties props = new BizplayProperties();
        props.setDefaultCorpUserId("30447");

        BizplaySettlementAgentServiceImple agent = new BizplaySettlementAgentServiceImple(
                sessions, guardrail, gateway, new FormSkeletonServiceImple(mapper),
                planPicker, followUp, slotFiller, Runnable::run, props, mapper);

        // --- turn 1: rich message fills slots, but LISTS the plans (no auto-pick) ----
        BizplayPlanAgentRequest t1 = new BizplayPlanAgentRequest();
        t1.setCorpNo("1234567890");
        t1.setMessage("settle my Gwangju trip in August on the corporate card");
        BizplayPlanAgentResponse r1 = agent.chat(t1, "test-token");
        String sessionId = r1.getSessionId();

        JsonNode slots = loadSlots(sessions, sessionId);
        System.out.println("data in hand after 1 message: " + slots);

        assertEquals("PLAN_SEARCH", r1.getIntent(), "a search must list the plans, never auto-import one");
        assertTrue(r1.getPendingChoices() != null && !r1.getPendingChoices().isEmpty(),
                "the matching plans are offered as pick chips");
        assertEquals("2026-08-01", slots.path("startDate").asText());
        assertEquals("2026-08-31", slots.path("endDate").asText());
        assertEquals("CORP", slots.path("cardTypes").get(0).asText(), "card type carried from the first message");
        assertTrue(slots.path("planHint").asText().toLowerCase().contains("gwangju"));
        assertTrue(slots.path("approvalId").isMissingNode() || slots.path("approvalId").asLong() == 0,
                "nothing imported yet — the user still has to pick");

        // --- turn 2: the user picks a plan from the list ---------------------------
        BizplayPlanAgentRequest t2 = new BizplayPlanAgentRequest();
        t2.setCorpNo("1234567890");
        t2.setSessionId(sessionId);
        t2.setMessage("settle-plan:9001");
        BizplayPlanAgentResponse r2 = agent.chat(t2, "test-token");
        assertEquals("PLAN_IMPORT", r2.getIntent());
        assertEquals(9001L, loadSlots(sessions, sessionId).path("approvalId").asLong());

        // --- turn 3: default evidence period → card type already in hand → skip ----
        BizplayPlanAgentRequest t3 = new BizplayPlanAgentRequest();
        t3.setCorpNo("1234567890");
        t3.setSessionId(sessionId);
        t3.setMessage("evidence-period:default");
        BizplayPlanAgentResponse r3 = agent.chat(t3, "test-token");
        assertEquals("EVIDENCE_LOAD", r3.getIntent(),
                "card-type question must be skipped — the card type was already gathered");
    }

    /** Read the persisted slot bag out of the session's agent_state event. */
    private JsonNode loadSlots(InMemorySessionRepo sessions, String sessionId) throws Exception {
        for (JsonNode e : sessions.get(UUID.fromString(sessionId)).getChatEventJson()) {
            if ("agent_state".equals(e.path("role").asText())) {
                return mapper.readTree(e.path("content").asText("{}")).path("slots");
            }
        }
        return mapper.createObjectNode();
    }

    /**
     * Manual expense entry (⑧): with no card receipt, the gathered expense is registered
     * (etc-card), its image uploaded (filebox), the issued detail fetched (issued/bulk), and mapped
     * into the settlement body's etcReceiptSaveRequests[] with EXACT keys — then totals recompute.
     */
    @Test
    void manualExpenseMapsExactKeysIntoEtcReceiptSaveRequests() throws Exception {
        InMemorySessionRepo sessions = new InMemorySessionRepo();
        GuardrailAgentService guardrail = mock(GuardrailAgentService.class);
        when(guardrail.check(anyString())).thenReturn(GuardrailAgentService.GuardrailResult.ok());
        PlanPickerAgentService planPicker = mock(PlanPickerAgentService.class);
        when(planPicker.pick(anyString(), any())).thenReturn(null);
        SlotFillerAgentService slotFiller = mock(SlotFillerAgentService.class);
        when(slotFiller.extract(anyString(), any(), anyBoolean())).thenReturn(mapper.createObjectNode());
        FormFollowUpAgentService followUp = mock(FormFollowUpAgentService.class);

        BizplayGatewayService gateway = mock(BizplayGatewayService.class);
        when(gateway.getPlanList(anyLong(), anyString(), anyString(), any())).thenReturn(planList());
        when(gateway.getPlanDetail(eq(9001L), any())).thenReturn(planDetail());
        when(gateway.getPapers(eq(102L), eq(52L), any())).thenReturn(papers());
        // ⑧ manual-expense gateway calls
        when(gateway.postEtcCardReceipts(any(), any())).thenReturn(List.of(777L));
        when(gateway.uploadReceiptFile(any(), any(), any())).thenReturn(888L);
        when(gateway.getIssuedReceiptsBulk(any(), any()))
                .thenReturn(mapper.readTree("[{\"id\":999,\"receiptId\":777}]"));
        when(gateway.patchEtcReceiptDetail(anyLong(), any(), any())).thenReturn("수정되었습니다.");

        BizplayProperties props = new BizplayProperties();
        props.setDefaultCorpUserId("161");
        BizplaySettlementAgentServiceImple agent = new BizplaySettlementAgentServiceImple(
                sessions, guardrail, gateway, new FormSkeletonServiceImple(mapper),
                planPicker, followUp, slotFiller, Runnable::run, props, mapper);

        // import a plan so a draft exists
        String sessionId = null;
        for (String msg : new String[]{"2026-08-01 ~ 2026-08-31", "settle-plan:9001"}) {
            BizplayPlanAgentRequest req = new BizplayPlanAgentRequest();
            req.setCorpNo("1234567890");
            req.setSessionId(sessionId);
            req.setMessage(msg);
            sessionId = agent.chat(req, "test-token").getSessionId();
        }

        // the 11 gathered fields, exactly the etc-card request-body sample
        JsonNode fields = mapper.readTree("""
                {"approvalDate":"2026-08-07","approvalTime":"19:37:31","currencyCode":"KRW",
                 "mestName":"Buffet","mestCorpNo":"1234567890","overseasUsed":false,
                 "approvalAmount":20000,"supplyAmount":18000,"originalSupplyAmount":18000,
                 "vatAmount":2000,"originalVatAmount":2000}""");
        // optional additional detail (ReceiptEtcDto) → PATCHed to the created receipt
        JsonNode detail = mapper.readTree(
                "{\"etcReceiptType\":\"RECEIPT\",\"usedStartDate\":\"2026-07-21\","
                        + "\"usedEndDate\":\"2026-07-21\",\"vehicleType\":\"AIRPORT_TRANSFER\","
                        + "\"depart\":\"incheon airport\",\"arrival\":\"KTI airport\"}");
        // PARTIAL two-phase: step 1 registers the base receipt, step 2 PATCHes the detail + maps.
        agent.createManualReceipt(sessionId, "1234567890", fields, "test-token");
        agent.completeManualReceipt(sessionId, "1234567890", detail,
                "img-bytes".getBytes(), "receipt.png", "test-token");

        // the additional detail was PATCHed to the created receipt id (777), not to some other id
        verify(gateway).patchEtcReceiptDetail(eq(777L), any(), eq("test-token"));

        JsonNode doc = sessions.get(UUID.fromString(sessionId)).getDraftJson().get(0);
        JsonNode etc = doc.path("etcReceiptSaveRequests");
        System.out.println("etcReceiptSaveRequests[0] = " + etc.path(0));

        assertEquals(1, etc.size(), "one manual expense recorded");
        JsonNode e = etc.get(0);
        // every field the user supplied is mapped 1:1
        for (String k : new String[]{"approvalDate", "approvalTime", "currencyCode", "mestName",
                "mestCorpNo", "overseasUsed", "approvalAmount", "supplyAmount", "originalSupplyAmount",
                "vatAmount", "originalVatAmount"}) {
            assertTrue(e.has(k), "etcReceiptSaveRequests entry is missing the exact key: " + k);
            assertEquals(fields.path(k), e.path(k), "value mismatch for key: " + k);
        }
        assertEquals(888L, e.path("imageIds").get(0).asLong(), "the uploaded image is attached");
        // The save DTO (EtcReceiptSaveRequest) rejects extras — no `id` / `issuedReceiptId` in the entry.
        assertTrue(e.path("id").isMissingNode(), "no `id` — BizPlay rejects it on save");
        assertTrue(e.path("issuedReceiptId").isMissingNode(), "no `issuedReceiptId` in the save body");
        // the expense counts toward the totals (no card receipts, so it's the whole amount)
        assertEquals(20000.0, doc.path("totalBstrAmount").asDouble(), 0.001);
        assertEquals(20000.0, doc.path("totalPersonalAmount").asDouble(), 0.001);
        System.out.println("✓ manual expense mapped exact keys; totalBstrAmount="
                + doc.path("totalBstrAmount").asDouble());
    }

    /**
     * ⑦ In-chat submit: the provider save fires only when the USER asks to submit at the end — not
     * automatically. "submit" before a plan is imported is a no-op; after the flow it POSTs the
     * finished draft and flips the session to POSTED.
     */
    @Test
    void submitOnlyPostsWhenTheUserAsksAtTheEnd() throws Exception {
        InMemorySessionRepo sessions = new InMemorySessionRepo();
        GuardrailAgentService guardrail = mock(GuardrailAgentService.class);
        when(guardrail.check(anyString())).thenReturn(GuardrailAgentService.GuardrailResult.ok());
        PlanPickerAgentService planPicker = mock(PlanPickerAgentService.class);
        when(planPicker.pick(anyString(), any())).thenReturn(null);
        SlotFillerAgentService slotFiller = mock(SlotFillerAgentService.class);
        when(slotFiller.extract(anyString(), any(), anyBoolean())).thenReturn(mapper.createObjectNode());
        FormFollowUpAgentService followUp = mock(FormFollowUpAgentService.class);

        BizplayGatewayService gateway = mock(BizplayGatewayService.class);
        when(gateway.getPlanList(anyLong(), anyString(), anyString(), any())).thenReturn(planList());
        when(gateway.getPlanDetail(eq(9001L), any())).thenReturn(planDetail());
        when(gateway.getPapers(eq(102L), eq(52L), any())).thenReturn(papers());
        when(gateway.getUnattachedReceipts(anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(receiptStream());
        when(gateway.postSettlementDraft(any(), anyString())).thenReturn("저장되었습니다.");

        BizplayProperties props = new BizplayProperties();
        props.setDefaultCorpUserId("30447");
        BizplaySettlementAgentServiceImple agent = new BizplaySettlementAgentServiceImple(
                sessions, guardrail, gateway, new FormSkeletonServiceImple(mapper),
                planPicker, followUp, slotFiller, Runnable::run, props, mapper);

        // "submit" with no draft yet → NOT a submit (no provider call)
        BizplayPlanAgentRequest early = new BizplayPlanAgentRequest();
        early.setCorpNo("1234567890");
        early.setMessage("submit");
        BizplayPlanAgentResponse r0 = agent.chat(early, "test-token");
        assertNotEquals("CREATE_SETTLEMENT", r0.getIntent(), "submit before import must be a no-op");

        // run the flow: search → import → evidence → attach → done
        String sessionId = null;
        for (String msg : new String[]{"2026-08-01 ~ 2026-08-31", "settle-plan:9001",
                "card-types:CORP,PERSONAL", "receipt:all", "receipts-done"}) {
            BizplayPlanAgentRequest req = new BizplayPlanAgentRequest();
            req.setCorpNo("1234567890");
            req.setSessionId(sessionId);
            req.setMessage(msg);
            BizplayPlanAgentResponse res = agent.chat(req, "test-token");
            sessionId = res.getSessionId();
        }
        verify(gateway, never()).postSettlementDraft(any(), anyString());   // not saved yet

        // the user asks to submit → NOW it posts
        BizplayPlanAgentRequest submit = new BizplayPlanAgentRequest();
        submit.setCorpNo("1234567890");
        submit.setSessionId(sessionId);
        submit.setMessage("제출해줘");
        BizplayPlanAgentResponse done = agent.chat(submit, "test-token");

        verify(gateway).postSettlementDraft(any(), eq("test-token"));
        verify(gateway, never()).postPlanDraft(any(), any());
        assertEquals("CREATE_SETTLEMENT", done.getIntent());
        assertEquals("POSTED", done.getStatus());
        System.out.println("✓ chat submit posts to /bstr/report/draft only on the user's ask; status=" + done.getStatus());
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

    /**
     * Two plans in DIFFERENT periods — the provider ignores the period query and returns the
     * traveler's whole history, so the agent's own period filter is what must narrow this.
     */
    private JsonNode planList() throws Exception {
        return mapper.readTree("""
                [{"approvalId":9001,"docNo":"BSTR-2026-0810","title":"부산 고객사 방문",
                  "bstrStartDate":"2026-08-10","bstrEndDate":"2026-08-12","bstrPurpose":"국내출장",
                  "draftUserName":"김민수","regUserName":"박서연"},
                 {"approvalId":9002,"docNo":"BSTR-2027-0301","title":"광주 협력사 방문",
                  "bstrStartDate":"2027-03-01T00:00:00.000Z","bstrEndDate":"2027-03-03T00:00:00.000Z",
                  "bstrPurpose":"국내출장","draftUserName":"김민수","regUserName":"김민수"}]
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
