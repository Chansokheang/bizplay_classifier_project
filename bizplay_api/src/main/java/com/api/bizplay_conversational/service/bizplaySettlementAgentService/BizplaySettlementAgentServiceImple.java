package com.api.bizplay_conversational.service.bizplaySettlementAgentService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayFormResponse;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.api.bizplay_conversational.repository.ConversationalAgentSessionRepo;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.api.bizplay_conversational.service.guardrailAgentService.GuardrailAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based settlement (출장정산) orchestrator. No LLM writes anything here: dates come
 * from deterministic parsing, the plan and the evidence come from BizPlay lookups
 * (④ plan list, ⑤ plan detail, ⑥ receipt stream), and every pick is a chip click.
 * The session's draft_json holds ONE settlement document shaped after the captured
 * 정산서 request-body sample — anchor fields from the picked plan, receipts appended
 * verbatim from the stream records, totals recomputed. Saving to BizPlay is a later phase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizplaySettlementAgentServiceImple implements BizplaySettlementAgentService {

    private static final String STATE_ROLE = "agent_state";
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern PLAN_PICK = Pattern.compile("(?i).*settle-plan:(\\d+).*");
    private static final Pattern PERIOD_DEFAULT = Pattern.compile("(?i).*evidence-period:default.*");
    private static final Pattern CARD_TYPES = Pattern.compile("(?i).*card-types:([A-Z_,]+).*");
    private static final Pattern RECEIPT_PICK = Pattern.compile("(?i).*receipt:(\\d+|all).*");
    private static final Pattern RECEIPTS_DONE = Pattern.compile("(?i).*(receipts-done|첨부 ?완료|evidence done).*");
    /** Receipts shown as chips per page — the rest stay listed in the reply text. */
    private static final int CHIP_LIMIT = 12;
    /** The settlement body's per-kind amount slots, in the sample's order. */
    private static final String[] COST_SLOTS = {"dailyCostAmount", "lodgingCostAmount",
            "fuelCostAmount", "foodCostAmount", "incidentalCostAmount", "publicFixCostAmount"};
    /**
     * Fixed-allowance tranKindType -> its amount slot. Keys are provider enum values
     * (BstrReceiptDto.tranKindType); kinds with no unambiguous slot are left out, so their amount
     * still counts toward the totals but no bucket is guessed for it.
     */
    private static final java.util.Map<String, String> COST_BUCKETS = java.util.Map.ofEntries(
            java.util.Map.entry("DAILY_COST", "dailyCostAmount"),
            java.util.Map.entry("HD_DAILY_COST", "dailyCostAmount"),
            java.util.Map.entry("ROOM", "lodgingCostAmount"),
            java.util.Map.entry("HD_ROOM", "lodgingCostAmount"),
            java.util.Map.entry("HD_LODGING_COST", "lodgingCostAmount"),
            java.util.Map.entry("FUEL", "fuelCostAmount"),
            java.util.Map.entry("HD_FUEL", "fuelCostAmount"),
            java.util.Map.entry("FOOD", "foodCostAmount"),
            java.util.Map.entry("HD_FOOD", "foodCostAmount"),
            java.util.Map.entry("HD_INCIDENTAL_COST", "incidentalCostAmount"));

    private final ConversationalAgentSessionRepo sessionRepo;
    private final GuardrailAgentService guardrailAgentService;
    private final BizplayGatewayService bizplayGatewayService;
    private final com.api.bizplay_conversational.service.formSkeletonService.FormSkeletonService formSkeletonService;
    private final com.api.bizplay_conversational.service.planPickerAgentService.PlanPickerAgentService planPickerAgentService;
    private final com.api.bizplay_conversational.service.formFollowUpAgentService.FormFollowUpAgentService formFollowUpAgentService;
    private final com.api.bizplay_conversational.config.BizplayProperties bizplayProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken) {
        if (request.getCorpNo() == null || request.getCorpNo().isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        if (request.getCorpUserId() == null || request.getCorpUserId().isBlank()) {
            request.setCorpUserId(bizplayProperties.getDefaultCorpUserId());
        }
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }
        boolean ko = koreanConversation(message);

        // Stage-machine chip tokens (settle-plan:{id}, evidence-period:default, receipt:all …)
        // are generated by our own UI and deterministic — never send them through the LLM
        // guardrail, which can misread a bare machine token as an injection attempt.
        boolean machineToken = message.matches(
                "(?i)\\s*(settle-plan:|card-types:|receipt:|receipts-done|evidence-period:).*");
        if (!machineToken) {
            GuardrailAgentService.GuardrailResult guard = guardrailAgentService.check(message);
            if (!guard.allowed()) {
                return BizplayPlanAgentResponse.builder()
                        .sessionId(request.getSessionId())
                        .intent("GUARDRAIL_BLOCKED")
                        .subAgents(List.of("GUARDRAIL_AGENT"))
                        .reply(guard.reply())
                        .build();
            }
        }

        ConversationalAgentSession session = resolveSession(request);
        ObjectNode state = loadState(session);
        ArrayNode documents = documents(session);

        // Sub-agent: Follow-up (draft Q&A) — free-form questions about the in-progress
        // settlement ("얼마 첨부했어?", "which trip did I pick?") answer from the draft
        // itself, without advancing the stage machine.
        boolean midSession = request.getSessionId() != null && !request.getSessionId().isBlank();
        if (midSession && !documents.isEmpty() && isDraftQuestion(message)) {
            String context = "STAGE: " + state.path("stage").asText("") + "\nANCHOR: "
                    + state.path("anchor") + "\nDOCUMENT: " + truncate(documents.get(0).toString(), 3500);
            String answer = formFollowUpAgentService.answerDraftQuestion(context, message, ko);
            appendTurn(session, "user", message);
            appendTurn(session, "assistant", answer);
            saveState(session, state);
            ConversationalAgentSession savedQa = sessionRepo.save(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(savedQa.getId().toString())
                    .status(savedQa.getStatus() == null ? null : savedQa.getStatus().name())
                    .intent("DRAFT_QUERY")
                    .subAgents(List.of("FOLLOW_UP_AGENT"))
                    .reply(answer)
                    .draftJson(savedQa.getDraftJson())
                    .build();
        }

        List<String> subAgents = new ArrayList<>();
        List<TripPlanAgentResponse.PendingChoice> chips = null;
        StringBuilder reply = new StringBuilder();
        String stage = state.path("stage").asText("");
        String intent;

        long corpUserId = Long.parseLong(request.getCorpUserId().trim());

        switch (stage) {
            case "AWAIT_PLAN_PICK" -> {
                Matcher m = PLAN_PICK.matcher(message);
                if (m.matches()) {
                    intent = "PLAN_IMPORT";
                    importPlan(state, documents, Long.parseLong(m.group(1)), corpUserId,
                            bizplayToken, subAgents, reply, ko);
                    chips = evidencePeriodChips(state, ko);
                } else {
                    String[] period = extractPeriod(message);
                    if (period != null) {
                        intent = "PLAN_SEARCH";
                        chips = searchPlans(state, corpUserId, period[0], period[1],
                                bizplayToken, subAgents, reply, ko);
                    } else {
                        // Sub-agent: Plan Picker — "the Busan education trip" resolves against
                        // the fetched candidates (deterministic match first, LLM on the miss).
                        Long picked = planPickerAgentService.pick(message, state.withArray("planCandidates"));
                        subAgents.add("PLAN_PICKER_AGENT");
                        if (picked != null) {
                            intent = "PLAN_IMPORT";
                            importPlan(state, documents, picked, corpUserId,
                                    bizplayToken, subAgents, reply, ko);
                            chips = evidencePeriodChips(state, ko);
                        } else {
                            intent = "PLAN_PICK_PENDING";
                            chips = planChipsFromState(state, ko);
                            reply.append(t(ko,
                                    "I couldn't match that to one plan — please pick from the list "
                                            + "(or give me a different date range). ",
                                    "말씀하신 내용과 정확히 일치하는 출장을 찾지 못했어요 — 목록에서 선택해 "
                                            + "주세요 (다른 기간을 말씀하셔도 됩니다). "));
                        }
                    }
                }
            }
            case "AWAIT_EVIDENCE_FILTER" -> {
                String[] period = PERIOD_DEFAULT.matcher(message).matches()
                        ? new String[]{state.path("anchor").path("startDate").asText(),
                                       state.path("anchor").path("endDate").asText()}
                        : extractPeriod(message);
                if (period != null) {
                    intent = "EVIDENCE_PERIOD";
                    state.put("evidenceStart", period[0]);
                    state.put("evidenceEnd", period[1]);
                    state.put("stage", "AWAIT_CARD_TYPES");
                    reply.append(t(ko,
                            "Evidence period set to " + period[0] + " ~ " + period[1]
                                    + ". Which card types should I search? ",
                            "증빙 조회 기간을 " + period[0] + " ~ " + period[1]
                                    + "(으)로 설정했어요. 어떤 카드의 사용 내역을 불러올까요? "));
                    chips = cardTypeChips(ko);
                } else {
                    intent = "EVIDENCE_PERIOD_PENDING";
                    // Sub-agent: Follow-up phrases the re-ask with the flow's own label.
                    reply.append(formFollowUpAgentService.composeFollowUp(
                            "출장정산서", List.of(t(ko, "evidence search period (증빙 조회 기간)", "증빙 조회 기간")), ko));
                    subAgents.add("FOLLOW_UP_AGENT");
                    chips = evidencePeriodChips(state, ko);
                }
            }
            case "AWAIT_CARD_TYPES" -> {
                Matcher m = CARD_TYPES.matcher(message.replace(" ", ""));
                List<String> types = m.matches() ? List.of(m.group(1).split(",")) : parseCardWords(message);
                if (!types.isEmpty()) {
                    intent = "EVIDENCE_LOAD";
                    chips = loadEvidence(state, documents, corpUserId, types, bizplayToken, subAgents, reply, ko);
                } else {
                    intent = "CARD_TYPES_PENDING";
                    reply.append(formFollowUpAgentService.composeFollowUp(
                            "출장정산서", List.of(t(ko, "card types to search (조회할 카드 종류)", "조회할 카드 종류")), ko));
                    subAgents.add("FOLLOW_UP_AGENT");
                    chips = cardTypeChips(ko);
                }
            }
            case "AWAIT_EVIDENCE_PICK" -> {
                Matcher m = RECEIPT_PICK.matcher(message);
                if (RECEIPTS_DONE.matcher(message).matches()) {
                    intent = "SETTLEMENT_READY";
                    state.put("stage", "DONE");
                    session.setStatus(ConversationalAgentSession.AgentStatus.READY_FOR_REVIEW);
                    summarize(documents, reply, ko);
                } else if (m.matches()) {
                    intent = "EVIDENCE_ATTACH";
                    attachReceipts(state, documents, m.group(1), reply, ko);
                    chips = receiptChips(state, documents, ko);
                } else {
                    intent = "EVIDENCE_PICK_PENDING";
                    reply.append(t(ko,
                            "Pick receipts to attach, or say \"done\" to finish. ",
                            "첨부할 증빙을 선택하거나 \"첨부 완료\"라고 말씀해 주세요. "));
                    chips = receiptChips(state, documents, ko);
                }
            }
            case "DONE" -> {
                intent = "SETTLEMENT_READY";
                summarize(documents, reply, ko);
            }
            default -> {
                // New settlement conversation: resolve the plan-search window first.
                String[] period = extractPeriod(message);
                if (period != null) {
                    intent = "PLAN_SEARCH";
                    chips = searchPlans(state, corpUserId, period[0], period[1],
                            bizplayToken, subAgents, reply, ko);
                } else {
                    intent = "AWAIT_PERIOD";
                    state.put("stage", "AWAIT_PLAN_PICK");   // next parsable period triggers the search
                    reply.append(t(ko,
                            "I'll find the trip to settle. Which period is it in? "
                                    + "(e.g. 2026-07-06 ~ 2026-08-06, \"last month\") ",
                            "정산할 출장을 찾아볼게요. 어느 기간의 출장인가요? "
                                    + "(예: 2026-07-06 ~ 2026-08-06, \"지난달\") "));
                }
            }
        }

        appendTurn(session, "user", message);
        appendTurn(session, "assistant", reply.toString());
        session.setDraftJson(documents);
        if (session.getStatus() == null) {
            session.setStatus(ConversationalAgentSession.AgentStatus.COLLECTING);
        }
        saveState(session, state);
        ConversationalAgentSession saved = sessionRepo.save(session);

        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent(intent)
                .subAgents(subAgents.isEmpty() ? List.of("SETTLEMENT_AGENT") : subAgents)
                .reply(reply.toString().trim())
                .pendingChoices(chips)
                .draftJson(saved.getDraftJson())
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    // --- stage steps -------------------------------------------------------------

    /** ④ Plan search -> candidate chips. Candidates cached in state for the pick turn. */
    private List<TripPlanAgentResponse.PendingChoice> searchPlans(
            ObjectNode state, long corpUserId, String start, String end, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        JsonNode list = bizplayGatewayService.getPlanList(corpUserId, start, end, token);
        subAgents.add("PLAN_SEARCH_TOOL");
        ArrayNode candidates = objectMapper.createArrayNode();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (list != null && list.isArray()) {
            for (JsonNode p : list) {
                // The list repeats one row per traveler/draft copy — dedupe for display.
                String dedupe = p.path("title").asText() + "|" + p.path("bstrStartDate").asText()
                        + "|" + p.path("bstrEndDate").asText();
                if (!seen.add(dedupe)) {
                    continue;
                }
                ObjectNode c = candidates.addObject();
                c.put("approvalId", p.path("approvalId").asLong());
                c.put("docNo", p.path("docNo").asText(""));
                c.put("title", p.path("title").asText(""));
                c.put("startDate", p.path("bstrStartDate").asText(""));
                c.put("endDate", p.path("bstrEndDate").asText(""));
                c.put("purpose", p.path("bstrPurpose").asText(""));
                if (candidates.size() >= 8) {
                    break;
                }
            }
        }
        state.set("planCandidates", candidates);
        state.put("stage", "AWAIT_PLAN_PICK");
        if (candidates.isEmpty()) {
            reply.append(t(ko,
                    "No trip plans found between " + start + " and " + end
                            + ". Try a different period. ",
                    start + " ~ " + end + " 기간에는 출장 계획이 없습니다. 다른 기간을 말씀해 주세요. "));
            return null;
        }
        reply.append(t(ko,
                "I found " + candidates.size() + " trip plan(s) between " + start + " and " + end
                        + ". Pick the one to settle. ",
                start + " ~ " + end + " 기간의 출장 계획 " + candidates.size()
                        + "건을 찾았습니다. 정산할 출장을 선택해 주세요. "));
        return planChipsFromState(state, ko);
    }

    /**
     * ⑤ Plan detail -> settlement document. The STRUCTURE never comes from here: it is the corp's
     * own EXPENSE_REPORT paper turned into the 정산서 request body by the Form Builder. This step
     * only fills the anchors that the picked plan determines (period, purpose, origin document)
     * and carries the plan's own item values over to the matching settlement items.
     */
    private void importPlan(ObjectNode state, ArrayNode documents, long approvalId, long corpUserId,
                            String token, List<String> subAgents, StringBuilder reply, boolean ko) {
        JsonNode d = bizplayGatewayService.getPlanDetail(approvalId, token);
        subAgents.add("PLAN_IMPORT_TOOL");

        long purposeId = d.path("bstrPurposeId").asLong();
        Long segmentId = d.hasNonNull("bstrSegmentId") ? d.path("bstrSegmentId").asLong() : null;
        JsonNode papers = bizplayGatewayService.getPapers(purposeId, segmentId, token);
        BizplayFormResponse form = formSkeletonService.buildSettlementSkeleton(papers, purposeId, segmentId);
        subAgents.add("FORM_BUILDER");

        String title = d.path("title").asText("");
        String start = d.path("bstrStartDate").asText("");
        String end = d.path("bstrEndDate").asText("");

        ObjectNode anchor = state.putObject("anchor");
        anchor.put("approvalId", approvalId);
        anchor.put("bstrId", d.path("bstrId").asLong());
        anchor.put("docNo", d.path("docNo").asText(""));
        anchor.put("title", title);
        anchor.put("startDate", start);
        anchor.put("endDate", end);
        anchor.put("purposeId", purposeId);
        anchor.put("paperId", form.getPaperId());

        ObjectNode doc = ((ObjectNode) form.getDocument()).deepCopy();
        String drafter = d.path("draftUserName").asText("");
        String paperName = form.getPaperName() == null ? "출장정산서" : form.getPaperName();
        doc.put("title", drafter.isEmpty() ? paperName + " - " + title : paperName + " - " + drafter);
        doc.put("content", d.path("content").asText(title));
        // Idempotency key of this settlement draft — one per imported plan, kept across turns.
        doc.put("clientRequestId", "er-" + LocalDate.now().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        doc.put("bstrPlanApprovalId", approvalId);
        copyOrNull(doc, "bstrType", d);
        copyOrNull(doc, "bstrPayType", d);
        doc.put("bstrStartDate", start);
        doc.put("bstrEndDate", end);
        doc.put("draftUserId", corpUserId);
        prefillIssuedItems(doc, d, start, end);
        // The drafter's own DRAFT line, exactly as the sample's first approval line. Approvers
        // beyond it are hand-picked in BizPlay — they cannot be derived from the paper.
        ObjectNode draftLine = doc.withArray("approvalLines").addObject();
        draftLine.put("approvalKindType", "DRAFT");
        draftLine.put("approvalOrder", 0);
        draftLine.put("corporationUserId", corpUserId);
        draftLine.putNull("departmentId");
        draftLine.put("departmentApproval", false);
        draftLine.put("paperApprovalLineType1", "EMPLOYEE");
        ObjectNode originEntry = doc.withArray("originDocuments").addObject();
        originEntry.put("originDocumentsId", approvalId);
        originEntry.put("note", "");
        documents.removeAll();
        documents.add(doc);

        state.put("stage", "AWAIT_EVIDENCE_FILTER");
        reply.append(t(ko,
                "Imported \"" + d.path("title").asText("") + "\" (" + d.path("bstrStartDate").asText("")
                        + " ~ " + d.path("bstrEndDate").asText("")
                        + "). Now let's load the card evidence — which period should I search? ",
                "\"" + d.path("title").asText("") + "\" (" + d.path("bstrStartDate").asText("")
                        + " ~ " + d.path("bstrEndDate").asText("")
                        + ") 계획을 불러왔습니다. 증빙을 조회할 기간을 알려주세요. "));
    }

    /**
     * Fill the settlement items the picked plan already determines — nothing is invented:
     *   BSTR_PERIOD   the trip period (selectionName = start, selectionErpCode = end)
     *   DAILY_COST    when the plan claimed 일비, one selections row per trip day
     *   POSTING_DATE  the drafting date (전기일 defaults to today, as in the BizPlay UI)
     *   everything else carried over from the plan's own issuedItems when the item ids match.
     */
    private void prefillIssuedItems(ObjectNode doc, JsonNode planDetail, String start, String end) {
        for (JsonNode row : doc.withArray("issuedItems")) {
            ObjectNode entry = (ObjectNode) row;
            String itemType = entry.path("item").path("itemType").asText("");
            long itemId = entry.path("item").path("id").asLong();
            JsonNode planned = plannedItem(planDetail, itemId, itemType);
            switch (itemType) {
                case "BSTR_PERIOD" -> {
                    ArrayNode selections = entry.putArray("selections");
                    addSelection(selections, null, start, end);
                }
                case "DAILY_COST" -> {
                    if (!"true".equals(planned.path("value").asText(""))) {
                        continue;   // the plan did not claim 일비 — leave the slot empty
                    }
                    entry.put("value", "true");
                    ArrayNode selections = entry.putArray("selections");
                    for (LocalDate day : days(start, end)) {
                        addSelection(selections, null, day.toString(), null);
                    }
                }
                case "POSTING_DATE" -> entry.put("value", LocalDate.now().toString());
                default -> {
                    if (planned.isMissingNode()) {
                        continue;
                    }
                    if (planned.hasNonNull("value")) {
                        entry.set("value", planned.get("value").deepCopy());
                    }
                    if (planned.hasNonNull("value2")) {
                        entry.set("value2", planned.get("value2").deepCopy());
                    }
                    ArrayNode selections = entry.putArray("selections");
                    for (JsonNode s : planned.path("selections")) {
                        // Normalised to the request body's five selection keys — the GET detail
                        // carries an extra selectionContent the save body does not use.
                        ObjectNode copy = addSelection(selections,
                                s.hasNonNull("selectionId") ? s.path("selectionId").asLong() : null,
                                s.path("selectionName").asText(null),
                                s.path("selectionErpCode").asText(null));
                        if (s.hasNonNull("selectionAreaInfo")) {
                            copy.set("selectionAreaInfo", s.get("selectionAreaInfo").deepCopy());
                        }
                    }
                }
            }
        }
    }

    /** The plan's issuedItems row for this settlement item — matched by item id, then by type. */
    private JsonNode plannedItem(JsonNode planDetail, long itemId, String itemType) {
        JsonNode byType = com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        for (JsonNode row : planDetail.path("issuedItems")) {
            if (row.path("item").path("id").asLong() == itemId) {
                return row;
            }
            if (byType.isMissingNode() && itemType.equals(row.path("item").path("itemType").asText())) {
                byType = row;
            }
        }
        return byType;
    }

    private ObjectNode addSelection(ArrayNode selections, Long selectionId, String name, String erpCode) {
        ObjectNode s = selections.addObject();
        if (selectionId == null) {
            s.putNull("selectionId");
        } else {
            s.put("selectionId", selectionId.longValue());
        }
        s.put("selectionName", name);
        s.put("selectionErpCode", erpCode);
        s.putNull("selectionMemo");
        s.putNull("selectionAreaInfo");
        return s;
    }

    private List<LocalDate> days(String start, String end) {
        List<LocalDate> days = new ArrayList<>();
        try {
            LocalDate from = LocalDate.parse(start);
            LocalDate to = LocalDate.parse(end);
            for (LocalDate day = from; !day.isAfter(to) && days.size() < 366; day = day.plusDays(1)) {
                days.add(day);
            }
        } catch (Exception e) {
            log.warn("Could not expand the trip period {} ~ {}: {}", start, end, e.getMessage());
        }
        return days;
    }

    /** ⑥ Receipt stream -> evidence chips. Trimmed candidates cached in state. */
    private List<TripPlanAgentResponse.PendingChoice> loadEvidence(
            ObjectNode state, ArrayNode documents, long corpUserId, List<String> cardTypes, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        String start = state.path("evidenceStart").asText(state.path("anchor").path("startDate").asText(""));
        String end = state.path("evidenceEnd").asText(state.path("anchor").path("endDate").asText(""));
        JsonNode receipts = bizplayGatewayService.getUnattachedReceipts(corpUserId, start, end, cardTypes, token);
        subAgents.add("RECEIPT_LOOKUP_TOOL");
        ArrayNode candidates = objectMapper.createArrayNode();
        if (receipts != null && receipts.isArray()) {
            for (JsonNode r : receipts) {
                if (r.path("approvalCanceled").asBoolean(false)) {
                    continue;   // canceled card approvals are not attachable evidence
                }
                candidates.add(trimReceipt(r));
                if (candidates.size() >= 50) {
                    break;
                }
            }
        }
        state.set("receiptCandidates", candidates);
        state.put("stage", "AWAIT_EVIDENCE_PICK");
        if (candidates.isEmpty()) {
            reply.append(t(ko,
                    "No unattached card receipts between " + start + " and " + end
                            + " for those card types. You can try another period or card type. ",
                    start + " ~ " + end + " 기간에 해당 카드의 미첨부 사용 내역이 없습니다. "
                            + "다른 기간이나 카드 종류로 다시 조회할 수 있어요. "));
            state.put("stage", "AWAIT_EVIDENCE_FILTER");
            return evidencePeriodChips(state, ko);
        }
        reply.append(t(ko,
                "Found " + candidates.size() + " unattached receipt(s) (" + start + " ~ " + end
                        + "). Pick the ones to attach. ",
                start + " ~ " + end + " 기간의 미첨부 증빙 " + candidates.size()
                        + "건을 찾았습니다. 첨부할 항목을 선택해 주세요. "));
        return receiptChipsFrom(candidates, documents.isEmpty() ? null : documents.get(0), ko);
    }

    /**
     * The evidence fields the settlement body needs, kept in agent state so the attach turn can
     * rebuild a full receipt row without re-querying the stream. The issued receipt (the ISSUED
     * side of the card approval, with its slip) rides along under "issuedReceipt" — its id is what
     * receiptIds and bstrReceipts[].id carry in the sample body.
     */
    private ObjectNode trimReceipt(JsonNode r) {
        ObjectNode c = objectMapper.createObjectNode();
        for (String k : new String[]{"id", "issuedReceiptId", "cardType", "etcReceiptType",
                "tranKindId", "tranKindType", "mestName", "approvalDate", "approvalTime",
                "approvalNumber", "approvalAmount", "approvalCanceled", "issuedAmt", "requestAmount",
                "supplyAmount", "vatAmount", "serviceCharge", "currencyCode", "exchangeRate",
                "cardNo", "cardNumber", "maskCardNumber", "bankCodeName", "usedStartDate",
                "usedEndDate", "deductionNonDeduction", "divisionType", "ruledAmount",
                "overseasApprovalAmount", "overseasRuledAmount", "depart", "arrival"}) {
            if (r.hasNonNull(k)) {
                c.set(k, r.get(k).deepCopy());
            }
        }
        ArrayNode imageIds = c.putArray("imageIds");
        for (JsonNode f : r.path("imageIds")) {
            imageIds.add(f.asLong());
        }
        for (JsonNode f : r.path("targetFileBoxDtos")) {
            if (f.hasNonNull("id")) {
                imageIds.add(f.path("id").asLong());
            }
        }
        JsonNode issued = r.path("issuedReceiptDtos").path(0);
        if (issued.isObject()) {
            ObjectNode ir = c.putObject("issuedReceipt");
            for (String k : new String[]{"id", "receiptId", "tranKindId", "tranKindType", "issuedAmt",
                    "splAmt", "vatAmt", "requestAmount"}) {
                if (issued.hasNonNull(k)) {
                    ir.set(k, issued.get(k).deepCopy());
                }
            }
            if (issued.hasNonNull("slip")) {
                ir.set("slip", issued.get("slip").deepCopy());
            }
        }
        return c;
    }

    /** Append picked receipt(s) to the settlement document + recompute the amount fields. */
    private void attachReceipts(ObjectNode state, ArrayNode documents, String pick,
                                StringBuilder reply, boolean ko) {
        if (documents.isEmpty()) {
            reply.append(t(ko, "No settlement draft in progress. ", "진행 중인 정산서가 없습니다. "));
            return;
        }
        ObjectNode doc = (ObjectNode) documents.get(0);
        ArrayNode candidates = (ArrayNode) state.withArray("receiptCandidates");
        boolean all = "all".equalsIgnoreCase(pick);
        long wantedId = all || !pick.matches("\\d+") ? -1 : Long.parseLong(pick);
        List<String> added = new ArrayList<>();
        for (JsonNode c : candidates) {
            long receiptId = c.path("id").asLong();
            if ((!all && receiptId != wantedId) || alreadyAttached(doc, receiptId)) {
                continue;
            }
            long issuedReceiptId = issuedReceiptId(c);
            doc.withArray("receiptIds").add(issuedReceiptId);
            doc.withArray("bstrReceipts").add(bstrReceipt(c, receiptId, issuedReceiptId));
            doc.withArray("issuedFields").add(issuedField(c, receiptId, issuedReceiptId, doc));
            added.add(c.path("mestName").asText("") + " ₩" + c.path("approvalAmount").asText(""));
        }
        recomputeTotals(doc);
        if (added.isEmpty()) {
            reply.append(t(ko, "That receipt is already attached. ", "이미 첨부된 증빙입니다. "));
        } else {
            reply.append(t(ko,
                    "Attached: " + String.join(", ", added) + ". ",
                    "첨부했습니다: " + String.join(", ", added) + ". "));
        }
    }

    /**
     * One bstrReceipts row, key-for-key as the captured sample. Every value comes from the receipt
     * record (or its slip); slots the stream does not carry stay null / 0 exactly as the sample
     * shows them for a receipt with no such data.
     */
    private ObjectNode bstrReceipt(JsonNode c, long receiptId, long issuedReceiptId) {
        JsonNode ir = c.path("issuedReceipt");
        JsonNode slip = ir.path("slip");
        double approval = c.path("approvalAmount").asDouble(0);
        double issuedAmt = firstNumber(ir.path("issuedAmt"), c.path("issuedAmt"), approval);
        double reqAmt = firstNumber(ir.path("requestAmount"), c.path("requestAmount"), approval);
        double supply = firstNumber(c.path("supplyAmount"), ir.path("splAmt"), approval);
        double vat = firstNumber(c.path("vatAmount"), ir.path("vatAmt"), 0);

        ObjectNode r = objectMapper.createObjectNode();
        r.put("id", issuedReceiptId);
        r.put("receiptId", receiptId);
        r.put("bstrReceiptType", c.path("cardType").asText(null));
        r.put("editable", true);
        copyNumberOrNull(r, "tranKindId", ir.path("tranKindId"), c.path("tranKindId"));
        r.put("tranKindType", firstText(ir.path("tranKindType"), c.path("tranKindType")));
        r.put("mestName", c.path("mestName").asText(null));
        r.put("approvalDate", c.path("approvalDate").asText(null));
        r.put("approvalTime", c.path("approvalTime").asText(null));
        r.put("approvalNumber", c.path("approvalNumber").asText(null));
        r.put("approvalAmount", approval);
        r.put("overseasApprovalAmount", c.path("overseasApprovalAmount").asDouble(0));
        r.put("approvalCanceled", c.path("approvalCanceled").asBoolean(false));
        r.put("settleAmount", issuedAmt);
        r.put("ruledAmount", c.path("ruledAmount").asDouble(0));
        r.put("overseasRuledAmount", c.path("overseasRuledAmount").asDouble(0));
        r.put("usedStartDate", c.path("usedStartDate").asText(c.path("approvalDate").asText(null)));
        r.put("usedEndDate", c.path("usedEndDate").asText(c.path("approvalDate").asText(null)));
        r.put("reqAmt", reqAmt);
        r.putNull("cancelReason");
        r.put("issuedAmt", issuedAmt);
        r.put("divisionType", c.path("divisionType").asText(null));
        r.put("bankCodeName", c.path("bankCodeName").asText(null));
        // Masked first: a card row carries both, and the draft has no business holding a full PAN.
        r.put("cardNo", firstText(c.path("maskCardNumber"), c.path("cardNumber"), c.path("cardNo")));
        r.put("bldat", c.path("approvalDate").asText(null));
        r.put("summary", slip.path("summary").asText(c.path("mestName").asText(null)));
        r.put("excessReason", "");
        copyNumberOrNull(r, "accountSubjectId", slip.path("accountSubjectId"));
        r.put("accountSubjectName", slip.path("accountSubjectName").asText(null));
        r.put("accountSubjectErpCode", slip.path("accountSubjectErpCode").asText(null));
        r.put("supplyAmount", supply);
        r.put("vatAmount", vat);
        r.put("nonDeduction", "NON_DEDUCTABLE".equals(c.path("deductionNonDeduction").asText("")));
        r.put("slipAmt", firstNumber(slip.path("slipAmt"), issuedAmt));
        r.put("slipSplAmt", firstNumber(slip.path("slipSplAmt"), supply));
        r.put("slipVatAmt", firstNumber(slip.path("slipVatAmt"), vat));
        copyNumberOrNull(r, "budgetDepartmentId", slip.path("budgetDepartmentId"));
        r.put("budgetDepartmentErpCode", slip.path("budgetDepartmentErpCode").asText(null));
        r.put("budgetDepartmentName", slip.path("budgetDepartmentName").asText(null));
        r.set("bstrRoutes", objectMapper.createArrayNode());
        r.put("documentBound", false);
        r.put("currencyCode", c.path("currencyCode").asText("KRW"));
        copyNumberOrNull(r, "exchangeRate", c.path("exchangeRate"));
        r.set("imageIds", c.path("imageIds").deepCopy());
        r.set("fileIds", c.path("imageIds").deepCopy());
        r.putNull("bstrPayClassType");
        r.put("serviceCharge", c.path("serviceCharge").asDouble(0));
        copyNumberOrNull(r, "taxCodeId", slip.path("taxCodeId"));
        r.put("taxName", slip.path("taxName").asText(null));
        r.putNull("taxCode");
        copyNumberOrNull(r, "internalOrderId", slip.path("internalOrderId"));
        r.putNull("internalOrderName");
        r.putNull("internalOrderErpCode");
        r.put("depart", c.path("depart").asText(null));
        r.put("arrival", c.path("arrival").asText(null));
        r.put("complianceTypesStr", "");
        r.put("displayHidden", false);
        return r;
    }

    /** The issuedFields entry pairing the receipt with its issued (slip-carrying) side. */
    private ObjectNode issuedField(JsonNode c, long receiptId, long issuedReceiptId, ObjectNode doc) {
        JsonNode ir = c.path("issuedReceipt");
        double approval = c.path("approvalAmount").asDouble(0);
        double issuedAmt = firstNumber(ir.path("issuedAmt"), c.path("issuedAmt"), approval);
        double supply = firstNumber(c.path("supplyAmount"), ir.path("splAmt"), approval);
        double vat = firstNumber(c.path("vatAmount"), ir.path("vatAmt"), 0);
        String etcType = c.path("etcReceiptType").asText("RECEIPT");

        ObjectNode field = objectMapper.createObjectNode();
        field.put("receiptId", receiptId);
        field.set("imageIds", c.path("imageIds").deepCopy());
        field.putObject("receiptEtc").put("etcReceiptType", etcType);

        ObjectNode dto = field.putArray("issuedReceiptDtos").addObject();
        dto.put("id", issuedReceiptId);
        dto.put("receiptId", receiptId);
        dto.put("reqAmt", firstNumber(ir.path("requestAmount"), c.path("requestAmount"), approval));
        dto.put("issuedAmt", issuedAmt);
        dto.put("splAmt", supply);
        dto.put("vatAmt", vat);
        dto.put("approvalAmount", approval);
        dto.put("ruledAmount", c.path("ruledAmount").asDouble(0));
        dto.put("overseasRuledAmount", c.path("overseasRuledAmount").asDouble(0));
        dto.put("overseasApprovalAmount", c.path("overseasApprovalAmount").asDouble(0));
        dto.put("currencyCode", c.path("currencyCode").asText("KRW"));
        copyNumberOrNull(dto, "exchangeRate", c.path("exchangeRate"));
        dto.put("bldat", c.path("approvalDate").asText(null));
        dto.set("imageIds", c.path("imageIds").deepCopy());
        dto.putObject("receiptEtc").put("etcReceiptType", etcType);
        dto.set("slip", slip(c, issuedAmt, supply, vat, doc));
        return field;
    }

    /**
     * The accounting slip. When the receipt already carries one it is echoed key-for-key (the
     * sample's slip shape); otherwise only the amounts, the summary and the posting date are
     * derivable — the accounting ids stay null for the approver to fill.
     */
    private ObjectNode slip(JsonNode c, double issuedAmt, double supply, double vat, ObjectNode doc) {
        JsonNode source = c.path("issuedReceipt").path("slip");
        ObjectNode s = objectMapper.createObjectNode();
        s.put("slipAmt", firstNumber(source.path("slipAmt"), issuedAmt));
        s.put("slipSplAmt", firstNumber(source.path("slipSplAmt"), supply));
        s.put("slipVatAmt", firstNumber(source.path("slipVatAmt"), vat));
        copyNumberOrNull(s, "budgetDepartmentId", source.path("budgetDepartmentId"));
        s.put("budgetDepartmentName", source.path("budgetDepartmentName").asText(null));
        s.put("budgetDepartmentErpCode", source.path("budgetDepartmentErpCode").asText(null));
        copyNumberOrNull(s, "accountSubjectId", source.path("accountSubjectId"));
        s.put("accountSubjectName", source.path("accountSubjectName").asText(null));
        s.put("accountSubjectErpCode", source.path("accountSubjectErpCode").asText(null));
        copyNumberOrNull(s, "taxCodeId", source.path("taxCodeId"));
        s.put("taxName", source.path("taxName").asText(null));
        copyNumberOrNull(s, "branchOfficeId", source.path("branchOfficeId"));
        copyNumberOrNull(s, "internalOrderId", source.path("internalOrderId"));
        copyNumberOrNull(s, "wbsId", source.path("wbsId"));
        copyNumberOrNull(s, "projectId", source.path("projectId"));
        s.put("projectName", source.path("projectName").asText(null));
        s.put("projectErpCode", source.path("projectErpCode").asText(null));
        s.put("summary", source.path("summary").asText(c.path("mestName").asText(null)));
        s.put("docDate", source.path("docDate").asText(postingDate(doc)));
        return s;
    }

    /** 전기일: whatever the POSTING_DATE item holds, else today. */
    private String postingDate(ObjectNode doc) {
        for (JsonNode row : doc.path("issuedItems")) {
            if ("POSTING_DATE".equals(row.path("item").path("itemType").asText())
                    && row.hasNonNull("value")) {
                return row.path("value").asText();
            }
        }
        return LocalDate.now().toString();
    }

    /**
     * Amount fields as the captured sample computes them: the totals span ALL evidence (card rows
     * + fixed-allowance rows), the settle/personal figure is the out-of-pocket side, and each cost
     * bucket sums the fixed-allowance (etcReceiptSaveRequests) rows of that kind — card receipts
     * never land in a bucket, exactly as the sample's FOOD receipt leaves foodCostAmount at 0.
     */
    private void recomputeTotals(ObjectNode doc) {
        double total = 0;
        double personal = 0;
        double point = 0;
        for (JsonNode r : doc.withArray("bstrReceipts")) {
            double amt = r.path("approvalAmount").asDouble(0);
            total += amt;
            String type = r.path("bstrReceiptType").asText("");
            if ("POINT".equals(type)) {
                point += amt;
            } else if (!"CORP".equals(type)) {
                personal += amt;   // corporate cards are paid by the company; the rest is reimbursed
            }
        }
        java.util.Map<String, Double> buckets = new java.util.HashMap<>();
        for (JsonNode e : doc.withArray("etcReceiptSaveRequests")) {
            double amt = e.path("approvalAmount").asDouble(0);
            total += amt;
            personal += amt;
            String bucket = COST_BUCKETS.get(e.path("tranKindType").asText(""));
            if (bucket != null) {
                buckets.merge(bucket, amt, Double::sum);
            }
        }
        doc.put("totalBstrAmount", total);
        doc.put("totalPointAmount", point);
        doc.put("totalPersonalAmount", personal);
        doc.put("totalSettleAmount", personal);
        for (String slot : COST_SLOTS) {
            doc.put(slot, buckets.getOrDefault(slot, 0d));
        }
    }

    private long issuedReceiptId(JsonNode c) {
        if (c.path("issuedReceipt").hasNonNull("id")) {
            return c.path("issuedReceipt").path("id").asLong();
        }
        return c.path("issuedReceiptId").asLong(c.path("id").asLong());
    }

    /** First numeric node with a value; {@code fallback} when none has one. */
    private double firstNumber(JsonNode a, JsonNode b, double fallback) {
        if (a.isNumber()) {
            return a.asDouble();
        }
        return b.isNumber() ? b.asDouble() : fallback;
    }

    private double firstNumber(JsonNode a, double fallback) {
        return a.isNumber() ? a.asDouble() : fallback;
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n.isTextual() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }

    /** Numeric slot: the first node that has a number, else an explicit null (never 0). */
    private void copyNumberOrNull(ObjectNode target, String key, JsonNode... sources) {
        for (JsonNode s : sources) {
            if (s.isNumber()) {
                target.set(key, s.deepCopy());
                return;
            }
        }
        target.putNull(key);
    }

    private void copyOrNull(ObjectNode target, String key, JsonNode source) {
        if (source.hasNonNull(key)) {
            target.set(key, source.get(key).deepCopy());
        } else {
            target.putNull(key);
        }
    }

    private void summarize(ArrayNode documents, StringBuilder reply, boolean ko) {
        if (documents.isEmpty()) {
            reply.append(t(ko, "No settlement draft yet. ", "아직 작성된 정산서가 없습니다. "));
            return;
        }
        JsonNode doc = documents.get(0);
        int n = doc.path("bstrReceipts").size();
        reply.append(t(ko,
                "Settlement draft for \"" + doc.path("title").asText("") + "\": " + n
                        + " receipt(s), total ₩" + doc.path("totalBstrAmount").asText("0")
                        + " (reimbursable ₩" + doc.path("totalSettleAmount").asText("0")
                        + "). The draft is stored — provider save comes in the next phase. ",
                "\"" + doc.path("title").asText("") + "\" 정산서: 증빙 " + n
                        + "건, 총액 ₩" + doc.path("totalBstrAmount").asText("0")
                        + " (개인 정산 ₩" + doc.path("totalSettleAmount").asText("0")
                        + "). 정산서 초안이 저장되었습니다 — 제출은 다음 단계에서 지원됩니다. "));
    }

    // --- chips -------------------------------------------------------------------

    private List<TripPlanAgentResponse.PendingChoice> planChipsFromState(ObjectNode state, boolean ko) {
        ArrayNode candidates = (ArrayNode) state.withArray("planCandidates");
        if (candidates.isEmpty()) {
            return null;
        }
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (JsonNode c : candidates) {
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(c.path("docNo").asText("") + " · " + c.path("title").asText("")
                            + " · " + c.path("startDate").asText("") + "~" + c.path("endDate").asText("")
                            + (c.path("purpose").asText("").isEmpty() ? "" : " · " + c.path("purpose").asText("")))
                    .sendText("settle-plan:" + c.path("approvalId").asLong())
                    .build());
        }
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("PLAN").name(t(ko, "trip to settle", "정산할 출장")).options(options).build());
    }

    private List<TripPlanAgentResponse.PendingChoice> evidencePeriodChips(ObjectNode state, boolean ko) {
        String start = state.path("anchor").path("startDate").asText("");
        String end = state.path("anchor").path("endDate").asText("");
        if (start.isEmpty()) {
            return null;
        }
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("EVIDENCE_PERIOD").name(t(ko, "evidence period", "증빙 조회 기간"))
                .options(List.of(TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Trip period (" + start + " ~ " + end + ")",
                                "출장 기간 그대로 (" + start + " ~ " + end + ")"))
                        .sendText("evidence-period:default")
                        .build()))
                .build());
    }

    private List<TripPlanAgentResponse.PendingChoice> cardTypeChips(boolean ko) {
        List<TripPlanAgentResponse.Option> options = List.of(
                TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Corporate card", "법인카드")).sendText("card-types:CORP").build(),
                TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Personal card", "개인카드")).sendText("card-types:PERSONAL").build(),
                TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Personal + MyData", "개인카드+마이데이터"))
                        .sendText("card-types:PERSONAL,MY_DATA").build(),
                TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "All cards", "전체"))
                        .sendText("card-types:CORP,PERSONAL,MY_DATA").build());
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("CARD_TYPE").name(t(ko, "card types", "카드 종류")).options(options).build());
    }

    private List<TripPlanAgentResponse.PendingChoice> receiptChips(ObjectNode state, ArrayNode documents, boolean ko) {
        return receiptChipsFrom((ArrayNode) state.withArray("receiptCandidates"),
                documents.isEmpty() ? null : documents.get(0), ko);
    }

    private List<TripPlanAgentResponse.PendingChoice> receiptChipsFrom(ArrayNode candidates, JsonNode doc, boolean ko) {
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (JsonNode c : candidates) {
            if (alreadyAttached(doc, c.path("id").asLong())) {
                continue;
            }
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(c.path("mestName").asText("") + " · " + c.path("approvalDate").asText("")
                            + " · ₩" + c.path("approvalAmount").asText("0")
                            + " · " + c.path("cardType").asText(""))
                    .sendText("receipt:" + c.path("id").asLong())
                    .build());
            if (options.size() >= CHIP_LIMIT) {
                break;
            }
        }
        options.add(TripPlanAgentResponse.Option.builder()
                .label(t(ko, "Attach all", "전체 첨부")).sendText("receipt:all").build());
        options.add(TripPlanAgentResponse.Option.builder()
                .label(t(ko, "Done", "첨부 완료")).sendText("receipts-done").build());
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("RECEIPT").name(t(ko, "evidence to attach", "첨부할 증빙")).options(options).build());
    }

    // --- deterministic parsing -----------------------------------------------------

    /** Two explicit ISO dates, or a relative Korean/English window word. Null = not given. */
    private String[] extractPeriod(String message) {
        List<String> dates = new ArrayList<>();
        Matcher m = ISO_DATE.matcher(message);
        while (m.find()) {
            dates.add(m.group(1));
        }
        if (dates.size() >= 2) {
            return new String[]{dates.get(0), dates.get(1)};
        }
        LocalDate today = LocalDate.now();
        String s = message.toLowerCase();
        if (s.contains("지난달") || s.contains("last month")) {
            LocalDate first = today.minusMonths(1).withDayOfMonth(1);
            return new String[]{first.toString(), first.plusMonths(1).minusDays(1).toString()};
        }
        if (s.contains("이번달") || s.contains("이번 달") || s.contains("this month")) {
            LocalDate first = today.withDayOfMonth(1);
            return new String[]{first.toString(), today.toString()};
        }
        if (s.contains("지난주") || s.contains("last week")) {
            LocalDate monday = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
            return new String[]{monday.toString(), monday.plusDays(6).toString()};
        }
        if (s.contains("최근") || s.contains("recent") || s.contains("이번주") || s.contains("this week")) {
            return new String[]{today.minusDays(30).toString(), today.toString()};
        }
        if (dates.size() == 1) {
            return new String[]{dates.get(0), dates.get(0)};
        }
        return null;
    }

    private List<String> parseCardWords(String message) {
        List<String> types = new ArrayList<>();
        String s = message.toLowerCase();
        if (s.contains("법인") || s.contains("corp")) {
            types.add("CORP");
        }
        if (s.contains("개인") || s.contains("personal")) {
            types.add("PERSONAL");
        }
        if (s.contains("마이데이터") || s.contains("my_data") || s.contains("mydata")) {
            types.add("MY_DATA");
        }
        if (s.contains("전체") || s.contains("all")) {
            return List.of("CORP", "PERSONAL", "MY_DATA");
        }
        return types;
    }

    /** Attachment is keyed by the RECEIPT id; receiptIds carries the issued-receipt ids instead. */
    private static boolean alreadyAttached(JsonNode doc, long receiptId) {
        if (doc == null) {
            return false;
        }
        for (JsonNode existing : doc.path("bstrReceipts")) {
            if (existing.path("receiptId").asLong() == receiptId) {
                return true;
            }
        }
        return false;
    }

    // --- session plumbing (same contract as the plan agent) ------------------------

    private ConversationalAgentSession resolveSession(BizplayPlanAgentRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            ConversationalAgentSession created = new ConversationalAgentSession();
            created.setCorpNo(request.getCorpNo());
            created.setAgentType(ConversationalAgentSession.AgentType.EXPENSE_REPORT);
            return created;
        }
        UUID id;
        try {
            id = UUID.fromString(sessionId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomNotFoundException("Invalid sessionId: " + sessionId);
        }
        ConversationalAgentSession existing = sessionRepo.findById(id)
                .orElseThrow(() -> new CustomNotFoundException("Session not found: " + sessionId));
        if (!request.getCorpNo().equals(existing.getCorpNo())) {
            throw new CustomNotFoundException(
                    "Session " + sessionId + " does not belong to corpNo=" + request.getCorpNo() + ".");
        }
        return existing;
    }

    private ArrayNode documents(ConversationalAgentSession session) {
        JsonNode draft = session.getDraftJson();
        return (draft instanceof ArrayNode a) ? a : objectMapper.createArrayNode();
    }

    private ObjectNode loadState(ConversationalAgentSession session) {
        JsonNode events = session.getChatEventJson();
        if (events != null && events.isArray()) {
            for (JsonNode e : events) {
                if (STATE_ROLE.equals(e.path("role").asText())) {
                    try {
                        JsonNode parsed = objectMapper.readTree(e.path("content").asText("{}"));
                        if (parsed instanceof ObjectNode o) {
                            return o;
                        }
                    } catch (Exception ex) {
                        log.warn("Could not parse settlement agent state; starting fresh: {}", ex.getMessage());
                    }
                }
            }
        }
        return objectMapper.createObjectNode();
    }

    private void saveState(ConversationalAgentSession session, ObjectNode state) {
        JsonNode existing = session.getChatEventJson();
        ArrayNode events = (existing instanceof ArrayNode a) ? a : objectMapper.createArrayNode();
        String content;
        try {
            content = objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            content = "{}";
        }
        for (JsonNode ev : events) {
            if (STATE_ROLE.equals(ev.path("role").asText())) {
                ((ObjectNode) ev).put("content", content);
                session.setChatEventJson(events);
                return;
            }
        }
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("role", STATE_ROLE);
        entry.put("content", content);
        entry.put("created_date", LocalDateTime.now().toString());
        events.add(entry);
        session.setChatEventJson(events);
    }

    private void appendTurn(ConversationalAgentSession session, String role, String content) {
        JsonNode existing = session.getChatEventJson();
        ArrayNode events = (existing instanceof ArrayNode a) ? a : objectMapper.createArrayNode();
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", role);
        turn.put("content", content == null ? "" : content);
        turn.put("created_date", LocalDateTime.now().toString());
        events.add(turn);
        session.setChatEventJson(events);
    }

    /** Same view/question heuristic as the plan agent's draft-QA gate. */
    private boolean isDraftQuestion(String m) {
        if (m == null || m.isBlank() || m.length() > 100) {
            return false;
        }
        // Stage-machine tokens and edits are never draft questions.
        if (m.matches("(?i).*(settle-plan:|card-types:|receipt:|receipts-done|evidence-period:).*")
                || m.matches("(?ius).*(add|change|set|remove|update|replace|make|추가|변경|수정|바꿔|빼|넣|삭제).*")) {
            return false;
        }
        boolean viewVerb = m.matches("(?ius).*(show|preview|view|display|summar|list|detail|status|so far|"
                + "보여|알려|요약|정리|현황|상태|지금까지).*");
        boolean question = m.contains("?")
                && m.matches("(?ius).*(what|which|who|when|where|how|is|are|did|do|"
                + "뭐|무엇|누구|언제|어디|몇|얼마|인가|나요|까요|어때).*");
        return viewVerb || question;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private boolean koreanConversation(String message) {
        long hangul = message.codePoints().filter(cp -> cp >= 0xAC00 && cp <= 0xD7A3).count();
        long latin = message.codePoints().filter(Character::isAlphabetic).count() - hangul;
        return hangul > 0 && hangul * 3 > latin;
    }

    private static String t(boolean ko, String en, String kr) {
        return ko ? kr : en;
    }
}
