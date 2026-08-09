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
    private static final Pattern TRANKIND_PICK = Pattern.compile("(?i).*trankind:(\\d+).*");
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

    /**
     * The session's "data in hand" — the slot bag persisted under agent_state.slots. Filled from
     * three sources (the static user seed, the user's answers, and endpoint responses) so nothing
     * is ever asked twice. Slot descriptors below are what the follow-up extractor understands.
     */
    private static final java.util.Map<String, String> SLOT_MEANINGS = java.util.Map.of(
            "startDate", "the trip-search period START date (ISO yyyy-MM-dd)",
            "endDate", "the trip-search period END date (ISO yyyy-MM-dd)",
            "cardTypes", "which cards to pull receipts for — a JSON array of CORP, PERSONAL, MY_DATA",
            "planHint", "any words identifying WHICH trip to settle (destination, purpose, or doc no)");

    /**
     * Required slot keys per BizPlay endpoint — the ask-only-missing gate. Before a call, whatever
     * is absent from the slot bag is what the agent asks for; when all are present it runs directly.
     * travelerId/corpUserId are seeded static, so in practice only the user-supplied slots gate.
     */
    private static final java.util.Map<String, java.util.List<String>> ENDPOINT_REQUIRED = java.util.Map.of(
            "planList", java.util.List.of("travelerId", "startDate", "endDate"),
            "planDetail", java.util.List.of("approvalId"),
            "receiptStream", java.util.List.of("corpUserId", "evidenceStart", "evidenceEnd", "cardTypes"));

    private final ConversationalAgentSessionRepo sessionRepo;
    private final GuardrailAgentService guardrailAgentService;
    private final BizplayGatewayService bizplayGatewayService;
    private final com.api.bizplay_conversational.service.formSkeletonService.FormSkeletonService formSkeletonService;
    private final com.api.bizplay_conversational.service.planPickerAgentService.PlanPickerAgentService planPickerAgentService;
    private final com.api.bizplay_conversational.service.formFollowUpAgentService.FormFollowUpAgentService formFollowUpAgentService;
    private final com.api.bizplay_conversational.service.slotFillerAgentService.SlotFillerAgentService slotFillerAgentService;
    /** Dedicated conversational fan-out pool (see AgentExecutorConfig) — field name matches the bean. */
    private final java.util.concurrent.Executor agentTaskExecutor;
    private final com.api.bizplay_conversational.config.BizplayProperties bizplayProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken) {
        if (request.getCorpNo() == null || request.getCorpNo().isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        // Settlement demo runs as ONE static user: travelerId == corpUserId == the configured
        // default, regardless of what the request carries. Seeded into the slot bag on load.
        request.setCorpUserId(bizplayProperties.getDefaultCorpUserId());
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }
        boolean ko = koreanConversation(message);

        // Stage-machine chip tokens (settle-plan:{id}, evidence-period:default, receipt:all …)
        // are generated by our own UI and deterministic — never send them through the LLM
        // guardrail, which can misread a bare machine token as an injection attempt.
        boolean machineToken = message.matches(
                "(?i)\\s*(settle-plan:|card-types:|receipt:|receipts-done|evidence-period:|manual-expense|submit|trankind:).*");
        // The guardrail check and the slot extraction both read only the raw message and have no
        // data dependency, so the orchestrator fans them out in PARALLEL on the agent pool — the
        // turn waits on the slower of the two, not their sum. (Data-dependent sub-agents such as the
        // plan-picker stay SEQUENTIAL, run later once the response they need exists.) Deterministic
        // chip tokens skip both — the LLM guardrail can misread a bare token as an injection.
        JsonNode extractedSlots = objectMapper.createObjectNode();
        if (!machineToken) {
            java.util.concurrent.CompletableFuture<GuardrailAgentService.GuardrailResult> guardFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(
                            () -> guardrailAgentService.check(message), agentTaskExecutor);
            java.util.concurrent.CompletableFuture<JsonNode> slotFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(
                            () -> slotFillerAgentService.extract(message, SLOT_MEANINGS, ko), agentTaskExecutor);
            GuardrailAgentService.GuardrailResult guard;
            try {
                guard = guardFuture.join();
                extractedSlots = slotFuture.join();
            } catch (Exception e) {
                log.warn("Parallel guardrail/slot stage failed ({}); treating the turn as allowed.", e.getMessage());
                guard = GuardrailAgentService.GuardrailResult.ok();
            }
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
        seedStaticUser(state);                              // travelerId/corpUserId slots = static default
        ArrayNode documents = documents(session);
        if (!machineToken) {
            gatherIntoSlots(state, message, extractedSlots);   // deterministic parse + merge LLM extraction
        }

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

        // Manual-expense. Bare "manual-expense" → ask which way to register (PARTIAL = base now +
        // details later via PATCH; COMPLETE = everything in one etc-card POST). The suffixed tokens
        // open the matching entry form (the actual create/PATCH happen on the /manual-expense/* endpoints).
        if (message.matches("(?i)\\s*manual-expense.*")) {
            boolean partial = message.matches("(?i).*manual-expense:partial.*");
            boolean complete = message.matches("(?i).*manual-expense:complete.*");
            String tkType = slots(state).path("evidenceTranKindType").asText(null);
            String tkName = slots(state).path("evidenceTranKindName").asText("");
            List<String> detailFields = detailFieldsForType(tkType);
            appendTurn(session, "user", message);
            if (!partial && !complete) {
                // ask the mode
                String ask = t(ko,
                        "How would you like to register" + (tkName.isEmpty() ? "" : " the " + tkName) + " receipt "
                                + "— partial (basics now, details later) or complete (everything at once)?",
                        (tkName.isEmpty() ? "" : tkName + " ") + "영수증을 어떻게 등록할까요 — 부분(기본 정보 먼저, "
                                + "상세는 나중에) 또는 전체(한 번에 모두)?");
                appendTurn(session, "assistant", ask);
                saveState(session, state);
                ConversationalAgentSession savedChoose = sessionRepo.save(session);
                return BizplayPlanAgentResponse.builder()
                        .sessionId(savedChoose.getId().toString())
                        .status(savedChoose.getStatus() == null ? null : savedChoose.getStatus().name())
                        .intent("MANUAL_EXPENSE_CHOOSE")
                        .subAgents(List.of("SETTLEMENT_AGENT"))
                        .reply(ask)
                        .pendingChoices(List.of(TripPlanAgentResponse.PendingChoice.builder()
                                .kind("MANUAL_EXPENSE_MODE").name(t(ko, "how to register", "등록 방식"))
                                .options(List.of(
                                        TripPlanAgentResponse.Option.builder()
                                                .label(t(ko, "Partial (basics first)", "부분 (기본 정보 먼저)"))
                                                .sendText("manual-expense:partial").build(),
                                        TripPlanAgentResponse.Option.builder()
                                                .label(t(ko, "Complete (all at once)", "전체 (한 번에)"))
                                                .sendText("manual-expense:complete").build()))
                                .build()))
                        .draftJson(savedChoose.getDraftJson())
                        .build();
            }
            String prompt = complete
                    ? t(ko, "Complete registration" + (tkName.isEmpty() ? "" : " for " + tkName)
                                + " — enter the expense and its details together. A receipt image is optional.",
                            "전체 등록" + (tkName.isEmpty() ? "" : " (" + tkName + ")") + " — 경비 정보와 상세를 "
                                + "함께 입력해 주세요. 영수증 이미지는 선택 사항입니다.")
                    : t(ko, "Partial registration — enter the basics" + (tkName.isEmpty() ? "" : " for " + tkName)
                                + " first; you'll add the details next.",
                            "부분 등록 — 먼저 기본 정보" + (tkName.isEmpty() ? "" : " (" + tkName + ")") + "를 입력하면 "
                                + "이어서 상세를 추가합니다.");
            appendTurn(session, "assistant", prompt);
            saveState(session, state);
            ConversationalAgentSession savedManual = sessionRepo.save(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(savedManual.getId().toString())
                    .status(savedManual.getStatus() == null ? null : savedManual.getStatus().name())
                    .intent(complete ? "MANUAL_EXPENSE_PROMPT_FULL" : "MANUAL_EXPENSE_PROMPT")
                    .subAgents(List.of("SETTLEMENT_AGENT"))
                    .reply(prompt)
                    .missingFields(detailFields)   // the type-specific detail inputs for the UI form
                    .draftJson(savedManual.getDraftJson())
                    .build();
        }

        // ⑦ Final submit — only when the USER asks to (a "submit" chip or "제출/save" in words), and
        // only once a plan has been imported (a real draft exists). POSTs the finished draft to
        // BizPlay's own /bstr/report/draft. Approver picks are handled by the separate create
        // endpoint; a chat submit carries just the drafter's DRAFT line.
        if (!documents.isEmpty() && isSubmitRequest(message)
                && state.path("anchor").hasNonNull("approvalId")) {
            String providerResponse = bizplayGatewayService.postSettlementDraft(documents, bizplayToken);
            log.info("Settlement draft submitted to BizPlay (chat): {}", providerResponse);
            session.setDraftJson(documents);
            session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
            state.put("stage", "DONE");
            String submitReply = t(ko,
                    "All done — your settlement (출장정산서) has been submitted to BizPlay.",
                    "완료됐어요 — 출장정산서를 BizPlay에 제출했습니다.");
            appendTurn(session, "user", message);
            appendTurn(session, "assistant", submitReply);
            saveState(session, state);
            ConversationalAgentSession savedSubmit = sessionRepo.save(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(savedSubmit.getId().toString())
                    .status(savedSubmit.getStatus() == null ? null : savedSubmit.getStatus().name())
                    .intent("CREATE_SETTLEMENT")
                    .subAgents(List.of("BIZPLAY_GATEWAY"))
                    .reply(submitReply)
                    .draftJson(savedSubmit.getDraftJson())
                    .build();
        }

        // "Done — no more expenses" (receipts-done) — works at ANY point after a plan is imported.
        // Without this, Done clicked from the card-type/manual stage fell through to a receipt
        // re-search ("No unattached card receipts…"). It finalizes: summarize + offer to submit.
        if (RECEIPTS_DONE.matcher(message).matches() && state.path("anchor").hasNonNull("approvalId")) {
            state.put("stage", "DONE");
            session.setStatus(ConversationalAgentSession.AgentStatus.READY_FOR_REVIEW);
            StringBuilder r = new StringBuilder();
            summarize(documents, r, ko);
            appendTurn(session, "user", message);
            appendTurn(session, "assistant", r.toString());
            session.setDraftJson(documents);
            saveState(session, state);
            ConversationalAgentSession savedDone = sessionRepo.save(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(savedDone.getId().toString())
                    .status(savedDone.getStatus() == null ? null : savedDone.getStatus().name())
                    .intent("SETTLEMENT_READY")
                    .subAgents(List.of("SETTLEMENT_AGENT"))
                    .reply(r.toString().trim())
                    .pendingChoices(submitChips(ko))
                    .draftJson(savedDone.getDraftJson())
                    .build();
        }

        // TranKind (re-)pick — from a chip (trankind:{id}) OR "register 숙박비" free text — works at
        // ANY point after a plan is imported, so the user can add expenses of DIFFERENT types. It
        // re-selects the TranKind and asks card types (→ search → attach or manual entry for that type).
        if (state.path("anchor").hasNonNull("approvalId")) {
            Long repick = null;
            Matcher tkm = TRANKIND_PICK.matcher(message);
            if (tkm.matches()) {
                repick = Long.parseLong(tkm.group(1));
            } else if (!machineToken && wantsRegister(message)) {
                repick = matchTranKindByName(state, message);
            }
            if (repick != null && hasTranKind(state, repick)) {
                selectTranKind(state, repick);
                state.put("stage", "AWAIT_CARD_TYPES");
                slots(state).remove("cardTypes");   // fresh card choice for the new type
                String tkName = slots(state).path("evidenceTranKindName").asText("");
                String r = t(ko,
                        (tkName.isBlank() ? "" : tkName + " — ") + "which card types should I search? ",
                        (tkName.isBlank() ? "" : tkName + " — ") + "어떤 카드의 사용 내역을 조회할까요? ");
                appendTurn(session, "user", message);
                appendTurn(session, "assistant", r);
                saveState(session, state);
                ConversationalAgentSession savedTk = sessionRepo.save(session);
                return BizplayPlanAgentResponse.builder()
                        .sessionId(savedTk.getId().toString())
                        .status(savedTk.getStatus() == null ? null : savedTk.getStatus().name())
                        .intent("TRANKIND_PICKED")
                        .subAgents(List.of("SETTLEMENT_AGENT"))
                        .reply(r)
                        .pendingChoices(cardTypeChips(ko))
                        .draftJson(savedTk.getDraftJson())
                        .build();
            }
        }

        List<String> subAgents = new ArrayList<>();
        if (!machineToken && extractedSlots != null && extractedSlots.size() > 0) {
            subAgents.add("SLOT_FILLER_AGENT");   // it pulled at least one parameter from this turn
        }
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
                    chips = afterImportChips(state, ko);
                } else {
                    String[] period = slotPeriod(state);
                    if (period != null) {
                        // A new period always re-lists the matching plans; the user picks one — we
                        // never auto-choose among several, even when the message named a place.
                        intent = "PLAN_SEARCH";
                        chips = searchPlans(state, corpUserId, period[0], period[1],
                                bizplayToken, subAgents, reply, ko);
                    } else {
                        // Sub-agent: Plan Picker — "the Busan education trip" resolves against
                        // the fetched candidates (deterministic match first, LLM on the miss).
                        String hint = slots(state).path("planHint").asText(message);
                        Long picked = planPickerAgentService.pick(hint, state.withArray("planCandidates"));
                        subAgents.add("PLAN_PICKER_AGENT");
                        if (picked != null) {
                            intent = "PLAN_IMPORT";
                            importPlan(state, documents, picked, corpUserId,
                                    bizplayToken, subAgents, reply, ko);
                            chips = afterImportChips(state, ko);
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
            case "AWAIT_TRANKIND" -> {
                Matcher tk = TRANKIND_PICK.matcher(message);
                if (tk.matches()) {
                    intent = "TRANKIND_PICKED";
                    selectTranKind(state, Long.parseLong(tk.group(1)));
                    state.put("stage", "AWAIT_CARD_TYPES");
                    reply.append(t(ko, "Which card types should I search? ",
                            "어떤 카드의 사용 내역을 조회할까요? "));
                    chips = cardTypeChips(ko);
                } else {
                    intent = "TRANKIND_PENDING";
                    reply.append(t(ko, "Please pick an expense type to add. ",
                            "추가할 경비 항목을 선택해 주세요. "));
                    chips = tranKindChips(state, ko);
                }
            }
            case "AWAIT_EVIDENCE_FILTER" -> {
                String[] period = PERIOD_DEFAULT.matcher(message).matches()
                        ? new String[]{state.path("anchor").path("startDate").asText(),
                                       state.path("anchor").path("endDate").asText()}
                        : extractPeriod(message);
                if (period != null) {
                    state.put("evidenceStart", period[0]);
                    state.put("evidenceEnd", period[1]);
                    slots(state).put("evidenceStart", period[0]);
                    slots(state).put("evidenceEnd", period[1]);
                    List<String> knownCards = cardTypesFromSlots(state);
                    if (!knownCards.isEmpty()) {
                        // Card types already in hand (from an earlier message) — don't ask again;
                        // required params for the receipt endpoint are complete, so run it now.
                        intent = "EVIDENCE_LOAD";
                        chips = loadEvidence(state, documents, corpUserId, knownCards,
                                bizplayToken, subAgents, reply, ko);
                    } else {
                        intent = "EVIDENCE_PERIOD";
                        state.put("stage", "AWAIT_CARD_TYPES");
                        reply.append(t(ko,
                                "Evidence period set to " + period[0] + " ~ " + period[1]
                                        + ". Which card types should I search? ",
                                "증빙 조회 기간을 " + period[0] + " ~ " + period[1]
                                        + "(으)로 설정했어요. 어떤 카드의 사용 내역을 불러올까요? "));
                        chips = cardTypeChips(ko);
                    }
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
                // Chip token wins; else whatever the slot-filler/parser already put in hand.
                List<String> types = m.matches() ? List.of(m.group(1).split(","))
                        : !cardTypesFromSlots(state).isEmpty() ? cardTypesFromSlots(state)
                        : parseCardWords(message);
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
                    chips = submitChips(ko);   // the draft is ready — offer to submit it to BizPlay
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
                chips = submitChips(ko);
            }
            default -> {
                // New settlement conversation: resolve the plan-search window first — from the
                // slot bag, which the gather step just filled from this message (deterministic + LLM).
                String[] period = slotPeriod(state);
                if (period != null) {
                    // Always list the matching plans for the user to pick — never auto-select one.
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
        state.put("lang", ko ? "ko" : "en");   // the create-flow reply reuses the conversation language
        // Readiness of each endpoint given the current data in hand — the ask-only-missing gate.
        log.info("settlement data-in-hand={} | planList needs {} | receiptStream needs {}",
                slots(state), missingSlots("planList", state), missingSlots("receiptStream", state));
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

    /**
     * ⑦ Submit the settlement: POST the session's draft_json to BizPlay's OWN settlement endpoint
     * (/bstr/report/draft via {@link BizplayGatewayService#postSettlementDraft}) — never the plan
     * draft path. The draft_json is already the 정산서 request-body shape, so it is posted as-is
     * after the optional approver picks are applied on top of the drafter's DRAFT line.
     */
    @Override
    @Transactional
    public BizplayPlanAgentResponse createSettlement(String sessionId, String corpNo,
                                                     String bizplayToken, JsonNode approvalLines) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        // Per-corp settings (BizPlay endpoint / product code) resolve through this context.
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            BizplayPlanAgentRequest lookup = new BizplayPlanAgentRequest();
            lookup.setCorpNo(corpNo);
            lookup.setSessionId(sessionId);
            ConversationalAgentSession session = resolveSession(lookup);
            ArrayNode documents = documents(session);
            if (documents.isEmpty()) {
                throw new IllegalArgumentException(
                        "This session has no settlement draft yet — import a plan and attach evidence first.");
            }
            ObjectNode state = loadState(session);
            boolean ko = "ko".equals(state.path("lang").asText(null));
            applyPickedApprovalLines((ObjectNode) documents.get(0), approvalLines);

            // POST the draft_json AS-IS — it already carries the 정산서 save-body structure.
            String providerResponse = bizplayGatewayService.postSettlementDraft(documents, bizplayToken);
            log.info("Settlement draft saved to BizPlay: {}", providerResponse);

            session.setDraftJson(documents);
            session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
            String reply = t(ko,
                    "All done — your settlement (출장정산서) has been saved to BizPlay.",
                    "완료됐어요 — 출장정산서가 BizPlay에 저장되었습니다.");
            appendTurn(session, "assistant", reply);
            saveState(session, state);
            sessionRepo.save(session);
            ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

            return BizplayPlanAgentResponse.builder()
                    .sessionId(saved.getId().toString())
                    .status(saved.getStatus() == null ? null : saved.getStatus().name())
                    .intent("CREATE_SETTLEMENT")
                    .subAgents(List.of("BIZPLAY_GATEWAY"))
                    .reply(reply)
                    .draftJson(saved.getDraftJson())
                    .createdDate(saved.getCreatedDate())
                    .updatedDate(saved.getUpdatedDate())
                    .build();
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    /** The etc-card / etcReceiptSaveRequests keys, mapped 1:1 between the two JSONs. */
    private static final String[] ETC_EXPENSE_KEYS = {
            "approvalDate", "approvalTime", "currencyCode", "mestName", "mestCorpNo", "overseasUsed",
            "approvalAmount", "supplyAmount", "originalSupplyAmount", "vatAmount", "originalVatAmount",
            "tranKindId", "tranKindType"};

    /**
     * ⑧ STEP 1 — register the receipt: POST /receipt/etc-card with just the base fields (+ the picked
     * TranKind). Returns the created receipt id, stashed in the session as slots.pendingReceipt so
     * STEP 2 can complete it. The additional detail + image are NOT collected here — that is
     * {@link #completeManualReceipt}.
     */
    @Override
    @Transactional
    public BizplayPlanAgentResponse createManualReceipt(String sessionId, String corpNo,
                                                        JsonNode expenseFields, String bizplayToken) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        if (expenseFields == null || !expenseFields.isObject()) {
            throw new IllegalArgumentException("Expense fields are required.");
        }
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            ConversationalAgentSession session = resolveSession(lookup(corpNo, sessionId));
            if (documents(session).isEmpty()) {
                throw new IllegalArgumentException("Import a plan before adding a manual expense.");
            }
            ObjectNode state = loadState(session);
            // PARTIAL create = just the base fields (no TranKind) — the detail is a separate PATCH.
            ObjectNode fields = prepareBaseFields(expenseFields, corpNo);
            long receiptId = createEtcReceipt(fields, bizplayToken);

            // Stash for STEP 2 (the detail PATCH + image + mapping run against this receipt).
            ObjectNode pending = slots(state).putObject("pendingReceipt");
            pending.put("receiptId", receiptId);
            pending.set("fields", fields);

            boolean ko = "ko".equals(state.path("lang").asText(null));
            java.util.List<String> detailFields = detailFieldsForType(slots(state).path("evidenceTranKindType").asText(null));
            String reply = t(ko,
                    "Receipt registered. Now add the details" + (detailFields.isEmpty() ? "" : " for this expense") + ".",
                    "영수증을 등록했어요. 이제 이 경비의 상세 정보를 입력해 주세요.");
            appendTurn(session, "assistant", reply);
            saveState(session, state);
            ConversationalAgentSession saved = sessionRepo.save(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(saved.getId().toString())
                    .status(saved.getStatus() == null ? null : saved.getStatus().name())
                    .intent("MANUAL_EXPENSE_CREATED")
                    .subAgents(List.of("BIZPLAY_GATEWAY"))
                    .reply(reply)
                    .missingFields(detailFields)   // the type-specific detail inputs for STEP 2
                    .draftJson(saved.getDraftJson())
                    .build();
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    /**
     * ⑧ STEP 2 — complete the receipt registered in STEP 1: PATCH /receipt-etc/{id} with the
     * additional detail, upload the optional image, and map the expense into the settlement draft's
     * etcReceiptSaveRequests. Requires a slots.pendingReceipt from {@link #createManualReceipt}.
     */
    @Override
    @Transactional
    public BizplayPlanAgentResponse completeManualReceipt(String sessionId, String corpNo, JsonNode detail,
                                                          byte[] image, String filename, String bizplayToken) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            ConversationalAgentSession session = resolveSession(lookup(corpNo, sessionId));
            ArrayNode documents = documents(session);
            if (documents.isEmpty()) {
                throw new IllegalArgumentException("No settlement draft in progress.");
            }
            ObjectNode state = loadState(session);
            JsonNode pending = slots(state).path("pendingReceipt");
            if (!pending.hasNonNull("receiptId")) {
                throw new IllegalArgumentException("No receipt to complete — register the receipt (step 1) first.");
            }
            long receiptId = pending.path("receiptId").asLong();
            ObjectNode fields = (ObjectNode) pending.path("fields").deepCopy();

            attachDetailImageAndMap(documents, receiptId, fields, detail, image, filename, bizplayToken);
            slots(state).remove("pendingReceipt");

            boolean ko = "ko".equals(state.path("lang").asText(null));
            String label = fields.path("mestName").asText("") + " ₩" + fields.path("approvalAmount").asText("");
            String reply = t(ko, "Added manual expense: " + label + ". ", "직접 입력 경비를 추가했어요: " + label + ". ");
            session.setDraftJson(documents);
            appendTurn(session, "assistant", reply);
            saveState(session, state);
            sessionRepo.save(session);
            ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(saved.getId().toString())
                    .status(saved.getStatus() == null ? null : saved.getStatus().name())
                    .intent("MANUAL_EXPENSE_ADDED")
                    .subAgents(List.of("BIZPLAY_GATEWAY"))
                    .reply(reply.trim())
                    .pendingChoices(addAnotherChips(state, ko))   // register another TranKind, or Done
                    .draftJson(saved.getDraftJson())
                    .createdDate(saved.getCreatedDate())
                    .updatedDate(saved.getUpdatedDate())
                    .build();
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    /**
     * COMPLETE mode — register the whole receipt in ONE etc-card POST: base + TranKind + detail +
     * imageIds, no separate PATCH. The full body IS a valid EtcReceiptSaveRequest, so it doubles as
     * the settlement entry.
     */
    @Override
    @Transactional
    public BizplayPlanAgentResponse addManualExpense(String sessionId, String corpNo, JsonNode expenseFields,
                                                     JsonNode detail, byte[] image, String filename, String bizplayToken) {
        if (expenseFields == null || !expenseFields.isObject()) {
            throw new IllegalArgumentException("Expense fields are required.");
        }
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            ConversationalAgentSession session = resolveSession(lookup(corpNo, sessionId));
            ArrayNode documents = documents(session);
            if (documents.isEmpty()) {
                throw new IllegalArgumentException("Import a plan before adding a manual expense.");
            }
            ObjectNode state = loadState(session);
            ObjectNode base = prepareExpenseFields(state, expenseFields, corpNo);   // base + TranKind
            // Upload the (optional) image first so its id rides in the single etc-card body.
            long fileId = 0;
            if (image != null && image.length > 0) {
                fileId = bizplayGatewayService.uploadReceiptFile(image, filename, bizplayToken);
            }
            ObjectNode fullBody = buildEtcExpense(base, detail, fileId);   // base + TranKind + detail + imageIds
            long newReceiptId = createEtcReceipt(fullBody, bizplayToken);   // single POST, no PATCH

            ObjectNode entry = fullBody.deepCopy();
            if (newReceiptId > 0) {
                entry.put("receiptId", newReceiptId);          // link to the created receipt (as in the sample)
                confirmIssued(entry, newReceiptId, bizplayToken);   // ISSUED? pull the server copy back
            }
            ObjectNode doc = (ObjectNode) documents.get(0);
            doc.withArray("etcReceiptSaveRequests").add(entry);
            recomputeTotals(doc);

            boolean ko = "ko".equals(state.path("lang").asText(null));
            String reply = t(ko, "Added manual expense.", "직접 입력 경비를 추가했어요.");
            session.setDraftJson(documents);
            appendTurn(session, "assistant", reply);
            sessionRepo.save(session);
            ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(saved.getId().toString())
                    .status(saved.getStatus() == null ? null : saved.getStatus().name())
                    .intent("MANUAL_EXPENSE_ADDED")
                    .subAgents(List.of("BIZPLAY_GATEWAY"))
                    .reply(reply)
                    .pendingChoices(addAnotherChips(state, ko))   // register another TranKind, or Done
                    .draftJson(saved.getDraftJson())
                    .build();
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    private BizplayPlanAgentRequest lookup(String corpNo, String sessionId) {
        BizplayPlanAgentRequest r = new BizplayPlanAgentRequest();
        r.setCorpNo(corpNo);
        r.setSessionId(sessionId);
        return r;
    }

    /** Finalize in OUR DB only — mark APPROVED + persist the current draft_json. No BizPlay POST. */
    @Override
    @Transactional
    public BizplayPlanAgentResponse saveSettlement(String corpNo, String sessionId) {
        ConversationalAgentSession session = resolveSession(lookup(corpNo, sessionId));
        session.setStatus(ConversationalAgentSession.AgentStatus.APPROVED);
        session.setDraftJson(session.getDraftJson());   // no-op touch; the blob is already current
        ConversationalAgentSession saved = sessionRepo.save(session);
        log.info("Settlement {} saved to our DB (status=APPROVED)", saved.getId());
        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent("SETTLEMENT_SAVED")
                .subAgents(List.of("SETTLEMENT_AGENT"))
                .reply("Saved to our database.")
                .draftJson(saved.getDraftJson())
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    /** Summary rows of this corp's settlements that carry a draft — for the saved-settlements table. */
    @Override
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> listSettlements(String corpNo) {
        java.util.List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (ConversationalAgentSession s : sessionRepo.findByCorpNo(corpNo)) {
            if (s.getAgentType() != ConversationalAgentSession.AgentType.EXPENSE_REPORT) {
                continue;   // settlements only, not plan sessions
            }
            ConversationalAgentSession full = sessionRepo.findById(s.getId()).orElse(s);
            JsonNode draft = full.getDraftJson();
            JsonNode doc = (draft != null && draft.isArray() && draft.size() > 0) ? draft.get(0) : null;
            JsonNode receipts = doc == null ? null : doc.path("etcReceiptSaveRequests");
            int count = (receipts != null && receipts.isArray()) ? receipts.size() : 0;
            if (doc == null || count == 0) {
                continue;   // skip empty settlements (nothing registered yet)
            }
            double total = 0;
            for (JsonNode r : receipts) {
                total += r.path("approvalAmount").asDouble(0);
            }
            if (total == 0) {
                total = doc.path("totalBstrAmount").asDouble(0);
            }
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("sessionId", s.getId().toString());
            row.put("status", s.getStatus() == null ? null : s.getStatus().name());
            row.put("title", doc.path("title").asText(doc.path("bstrTitle").asText("출장정산서")));
            row.put("total", total);
            row.put("receiptCount", count);
            row.put("createdDate", full.getCreatedDate());
            row.put("updatedDate", full.getUpdatedDate());
            out.add(row);
        }
        return out;
    }

    /** Read-only session load — draft_json (the registered expenses) + status, for the UI to restore. */
    @Override
    @Transactional(readOnly = true)
    public BizplayPlanAgentResponse getSession(String corpNo, String sessionId) {
        ConversationalAgentSession session = resolveSession(lookup(corpNo, sessionId));
        return BizplayPlanAgentResponse.builder()
                .sessionId(session.getId() == null ? null : session.getId().toString())
                .status(session.getStatus() == null ? null : session.getStatus().name())
                .intent("SESSION")
                .subAgents(List.of("SETTLEMENT_AGENT"))
                .draftJson(session.getDraftJson())
                .createdDate(session.getCreatedDate())
                .updatedDate(session.getUpdatedDate())
                .build();
    }

    /** deepCopy + default mestCorpNo + carry the picked TranKind into the etc-card body. */
    private ObjectNode prepareExpenseFields(ObjectNode state, JsonNode expenseFields, String corpNo) {
        ObjectNode fields = (ObjectNode) expenseFields.deepCopy();
        if (!fields.hasNonNull("mestCorpNo")) {
            fields.put("mestCorpNo", corpNo);
        }
        ObjectNode slots = slots(state);
        if (!fields.hasNonNull("tranKindId") && slots.hasNonNull("evidenceTranKindId")) {
            fields.put("tranKindId", slots.path("evidenceTranKindId").asLong());
        }
        if (!fields.hasNonNull("tranKindType") && slots.hasNonNull("evidenceTranKindType")) {
            fields.put("tranKindType", slots.path("evidenceTranKindType").asText());
        }
        return fields;
    }

    /** STEP 1 core: POST /receipt/etc-card → the created receipt id (0 when none returned). */
    private long createEtcReceipt(JsonNode fields, String token) {
        ArrayNode payload = objectMapper.createArrayNode();
        payload.add(fields.deepCopy());
        java.util.List<Long> ids = bizplayGatewayService.postEtcCardReceipts(payload, token);
        return ids.isEmpty() ? 0 : ids.get(0);
    }

    /** STEP 2 core: PATCH detail (best-effort) + upload optional image + map into the draft. */
    private void attachDetailImageAndMap(ArrayNode documents, long receiptId, ObjectNode fields,
                                         JsonNode detail, byte[] image, String filename, String token) {
        if (receiptId > 0 && detail != null && detail.isObject() && detail.size() > 0) {
            try {
                bizplayGatewayService.patchEtcReceiptDetail(receiptId, detail, token);
            } catch (RuntimeException e) {
                log.warn("receipt-etc detail update failed for {} — keeping the expense: {}", receiptId, e.getMessage());
            }
        }
        long fileId = 0;
        if (image != null && image.length > 0) {
            fileId = bizplayGatewayService.uploadReceiptFile(image, filename, token);
        }
        ObjectNode entry = buildEtcExpense(fields, detail, fileId);
        if (receiptId > 0) {
            entry.put("receiptId", receiptId);
            confirmIssued(entry, receiptId, token);   // ISSUED? mark it and pull the issued id back
        }
        ObjectNode doc = (ObjectNode) documents.get(0);
        doc.withArray("etcReceiptSaveRequests").add(entry);
        recomputeTotals(doc);
    }

    /**
     * Retrieve the created receipt via GET /receipt/issued/bulk/{id} — the ONLY receipts it returns
     * are ISSUED (complete). A partial (base-only) create is NOT_ISSUED and comes back empty, so this
     * doubles as the "is it attachable yet?" check. Marks the draft entry with the status + issued id.
     */
    private void confirmIssued(ObjectNode entry, long receiptId, String token) {
        try {
            JsonNode issued = bizplayGatewayService.getIssuedReceiptsBulk(java.util.List.of(receiptId), token);
            if (issued != null && issued.isArray() && issued.size() > 0) {
                entry.put("expenseStatus", "ISSUED");
                long issuedReceiptId = issued.get(0).path("id").asLong(0);
                if (issuedReceiptId > 0) {
                    entry.put("issuedReceiptId", issuedReceiptId);   // needed by the attach/enrichment step
                }
                log.info("etc receipt {} is ISSUED (issuedReceiptId={})", receiptId, issuedReceiptId);
            } else {
                entry.put("expenseStatus", "NOT_ISSUED");
                log.info("etc receipt {} is NOT_ISSUED (issued/bulk empty) — complete it to make it attachable", receiptId);
            }
        } catch (RuntimeException e) {
            log.warn("issued/bulk confirm failed for {}: {}", receiptId, e.getMessage());
        }
    }

    /** deepCopy + default mestCorpNo only (PARTIAL create = the base fields, no TranKind). */
    private ObjectNode prepareBaseFields(JsonNode expenseFields, String corpNo) {
        ObjectNode fields = (ObjectNode) expenseFields.deepCopy();
        if (!fields.hasNonNull("mestCorpNo")) {
            fields.put("mestCorpNo", corpNo);
        }
        return fields;
    }

    /**
     * One etcReceiptSaveRequests[] entry, keys mapped 1:1 from the etc-card request body, plus the
     * uploaded image (imageIds) and links to the created/issued receipt. recomputeTotals then counts
     * its approvalAmount toward the totals (and a cost bucket when it carries a tranKindType).
     */
    private ObjectNode buildEtcExpense(JsonNode fields, JsonNode detail, long fileId) {
        // The EtcReceiptSaveRequest keys (base + tranKind) + the ReceiptEtcDto detail merged in —
        // both are the same DTO. Drop `id`/`issuedReceiptId` (the save 400s on them). imageIds valid.
        ObjectNode e = objectMapper.createObjectNode();
        for (String k : ETC_EXPENSE_KEYS) {
            if (fields.has(k)) {
                e.set(k, fields.get(k).deepCopy());
            }
        }
        if (detail != null && detail.isObject()) {
            detail.fields().forEachRemaining(en -> {
                if (!"id".equals(en.getKey()) && !"issuedReceiptId".equals(en.getKey())) {
                    e.set(en.getKey(), en.getValue() == null ? null : en.getValue().deepCopy());
                }
            });
        }
        e.remove("id");
        e.remove("issuedReceiptId");
        ArrayNode imageIds = e.putArray("imageIds");
        if (fileId > 0) {
            imageIds.add(fileId);   // only when an image was actually uploaded
        }
        return e;
    }

    /**
     * Apply the UI's "Set approval order" picks on top of the drafter's own DRAFT line. Mirrors the
     * plan agent: the DRAFT line (already added at plan import) is kept at order 0, picked approvers
     * follow as APPROVAL lines. Without picks the draft keeps only its DRAFT line, exactly as the
     * captured sample's first approval entry.
     */
    private void applyPickedApprovalLines(ObjectNode master, JsonNode approvalLines) {
        if (approvalLines == null || !approvalLines.isArray() || approvalLines.isEmpty()) {
            return;
        }
        ArrayNode lines = objectMapper.createArrayNode();
        master.set("approvalLines", lines);
        ensureDraftApprovalLine(master);
        int order = 1;
        for (JsonNode pick : approvalLines) {
            if (!pick.path("corporationUserId").canConvertToLong()) {
                continue;
            }
            ObjectNode line = lines.addObject();
            line.put("approvalKindType", pick.path("approvalKindType").asText("APPROVAL"));
            line.put("approvalOrder", order++);
            line.put("corporationUserId", pick.path("corporationUserId").asLong());
            line.putNull("departmentId");
            line.put("departmentApproval", false);
            line.put("paperApprovalLineType1", "EMPLOYEE");
        }
    }

    /** The drafter's DRAFT line, rebuilt from draftUserId when the picks reset the array. */
    private void ensureDraftApprovalLine(ObjectNode document) {
        ArrayNode lines = document.withArray("approvalLines");
        for (JsonNode l : lines) {
            if ("DRAFT".equals(l.path("approvalKindType").asText())) {
                return;
            }
        }
        ObjectNode draft = lines.addObject();
        draft.put("approvalKindType", "DRAFT");
        draft.put("approvalOrder", 0);
        draft.set("corporationUserId", document.path("draftUserId").deepCopy());
        draft.putNull("departmentId");
        draft.put("departmentApproval", false);
        draft.put("paperApprovalLineType1", "EMPLOYEE");
    }

    // --- stage steps -------------------------------------------------------------

    /** ④ Plan search -> candidate chips. Candidates cached in state for the pick turn. */
    private List<TripPlanAgentResponse.PendingChoice> searchPlans(
            ObjectNode state, long corpUserId, String start, String end, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        JsonNode list = bizplayGatewayService.getPlanList(corpUserId, start, end, token);
        subAgents.add("PLAN_SEARCH_TOOL");
        // The provider IGNORES the period query — /bstr/plan/list returns the traveler's whole
        // plan history whatever startDate/endDate we send. So the period is applied here: keep
        // the plans that overlap it, and only fall back to the most recent ones when none does
        // (otherwise the reply would claim plans "between X and Y" while listing next year's).
        ArrayNode candidates = planCandidates(list, start, end);
        boolean inPeriod = !candidates.isEmpty();
        if (!inPeriod) {
            candidates = planCandidates(list, null, null);
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
        if (inPeriod) {
            reply.append(t(ko,
                    "I found " + candidates.size() + " trip plan(s) between " + start + " and " + end
                            + ". Pick the one to settle. ",
                    start + " ~ " + end + " 기간의 출장 계획 " + candidates.size()
                            + "건을 찾았습니다. 정산할 출장을 선택해 주세요. "));
        } else {
            reply.append(t(ko,
                    "No trip plan falls between " + start + " and " + end + " — here are the "
                            + candidates.size() + " most recent ones. Pick the one to settle, "
                            + "or give me another period. ",
                    start + " ~ " + end + " 기간에 해당하는 출장 계획은 없어서 최근 "
                            + candidates.size() + "건을 보여 드립니다. 정산할 출장을 선택하시거나 "
                            + "다른 기간을 말씀해 주세요. "));
        }
        return planChipsFromState(state, ko);
    }

    /**
     * Plan rows -> display candidates: deduped (the list repeats one row per traveler/draft copy),
     * capped at 8, and — when {@code start}/{@code end} are given — limited to the plans whose own
     * period overlaps them.
     */
    private ArrayNode planCandidates(JsonNode list, String start, String end) {
        ArrayNode candidates = objectMapper.createArrayNode();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (list == null || !list.isArray()) {
            return candidates;
        }
        for (JsonNode p : list) {
            String from = day(p.path("bstrStartDate").asText(""));
            String to = day(p.path("bstrEndDate").asText(""));
            if (start != null) {
                // Overlap test on ISO days (lexical order == chronological order).
                if (from.isEmpty() || to.isEmpty() || from.compareTo(end) > 0 || to.compareTo(start) < 0) {
                    continue;
                }
            }
            if (!seen.add(p.path("title").asText() + "|" + from + "|" + to)) {
                continue;
            }
            ObjectNode c = candidates.addObject();
            c.put("approvalId", p.path("approvalId").asLong());
            c.put("docNo", p.path("docNo").asText(""));
            c.put("title", p.path("title").asText(""));
            c.put("startDate", from);
            c.put("endDate", to);
            c.put("purpose", p.path("bstrPurpose").asText(""));
            c.put("drafter", p.path("draftUserName").asText(""));
            c.put("registrar", p.path("regUserName").asText(""));
            if (candidates.size() >= 8) {
                break;
            }
        }
        return candidates;
    }

    /** "2026-08-09T00:00:00.000Z" / "2026-08-09" -> "2026-08-09" (the provider sends both). */
    private String day(String value) {
        return value == null ? "" : (value.length() >= 10 ? value.substring(0, 10) : value);
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

        // Data in hand from the plan-detail response — so downstream endpoints never re-ask for it.
        ObjectNode slots = slots(state);
        slots.put("approvalId", approvalId);
        slots.put("purposeId", purposeId);
        if (segmentId != null) {
            slots.put("segmentId", segmentId);
        } else {
            slots.putNull("segmentId");
        }
        slots.put("tripStartDate", start);
        slots.put("tripEndDate", end);
        // The receipt sections' allowed TranKinds (paper.paperItemOrderDto[].itemDto.tranKinds),
        // resolved to {id, name, type} — drives the "find receipts by TranKind" chips + manual entry.
        resolvePlanTranKinds(state, d, token, subAgents);

        ObjectNode doc = ((ObjectNode) form.getDocument()).deepCopy();
        String drafter = d.path("draftUserName").asText("");
        String paperName = form.getPaperName() == null ? "출장정산서" : form.getPaperName();
        doc.put("title", drafter.isEmpty() ? paperName + " - " + title : paperName + " - " + drafter);
        doc.put("content", d.path("content").asText(title));
        // Idempotency key of this settlement draft — one per imported plan, kept across turns.
        doc.put("clientRequestId", "er-" + LocalDate.now().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        doc.put("bstrPlanApprovalId", approvalId);
        // bstrType (DOMESTIC | OVERSEA) lives on the paper in the plan detail — the top level is null.
        String bstrType = d.hasNonNull("bstrType") ? d.path("bstrType").asText()
                : d.path("paper").path("bstrType").asText(null);
        if (bstrType != null && !bstrType.isBlank()) {
            doc.put("bstrType", bstrType);
            slots(state).put("bstrType", bstrType);
        } else {
            doc.putNull("bstrType");
        }
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

        // Default the evidence search window to the trip period — don't ask for it again.
        slots(state).put("evidenceStart", start);
        slots(state).put("evidenceEnd", end);
        state.put("evidenceStart", start);
        state.put("evidenceEnd", end);

        String head = t(ko,
                "Imported \"" + title + "\" (" + start + " ~ " + end + "). ",
                "\"" + title + "\" (" + start + " ~ " + end + ") 계획을 불러왔습니다. ");
        if (!slots(state).withArray("planTranKinds").isEmpty()) {
            // TranKind-driven: pick which expense type to add first (drives the receipt search).
            state.put("stage", "AWAIT_TRANKIND");
            reply.append(head).append(t(ko,
                    "Which expense type would you like to add? ",
                    "어떤 경비 항목을 추가할까요? "));
        } else {
            state.put("stage", "AWAIT_CARD_TYPES");
            reply.append(head).append(t(ko,
                    "Which card types should I search for evidence? ",
                    "증빙을 조회할 카드 종류를 알려주세요. "));
        }
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
    /** Widen [start,end] by −7 / +30 days so the search catches receipts dated near/just after the
     * trip; returns the input unchanged when the dates don't parse. */
    private String[] widenWindow(String start, String end) {
        try {
            java.time.LocalDate s = java.time.LocalDate.parse(start).minusDays(7);
            java.time.LocalDate e = java.time.LocalDate.parse(end).plusDays(30);
            return new String[]{s.toString(), e.toString()};
        } catch (RuntimeException ex) {
            return new String[]{start, end};
        }
    }

    private List<TripPlanAgentResponse.PendingChoice> loadEvidence(
            ObjectNode state, ArrayNode documents, long corpUserId, List<String> cardTypes, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        String tripStart = state.path("evidenceStart").asText(state.path("anchor").path("startDate").asText(""));
        String tripEnd = state.path("evidenceEnd").asText(state.path("anchor").path("endDate").asText(""));
        // Widen the search window generously around the trip — manually-registered receipts often carry
        // dates a few days either side (or ahead) of the trip, so an exact-trip window silently misses
        // them. The receipt browser (calendar) is the precise tool when a tighter window is wanted.
        String[] win = widenWindow(tripStart, tripEnd);
        String start = win[0], end = win[1];
        // Record the receipt-endpoint params in the slot bag (data in hand for this + later turns).
        ObjectNode slots = slots(state);
        slots.put("evidenceStart", start);
        slots.put("evidenceEnd", end);
        ArrayNode ct = slots.putArray("cardTypes");
        cardTypes.forEach(ct::add);
        // No TranKind scoping — the provider's filter is (tranKindId IN ids OR IS NULL), which returns
        // the same rows anyway, and an unscoped search matches the user's working call (tranKindIds=).
        JsonNode receipts = bizplayGatewayService.getUnattachedReceipts(
                corpUserId, start, end, cardTypes, null, null, token);
        subAgents.add("RECEIPT_LOOKUP_TOOL");
        ArrayNode candidates = objectMapper.createArrayNode();
        if (receipts != null && receipts.isArray()) {
            for (JsonNode r : receipts) {
                if (r.path("approvalCanceled").asBoolean(false)) {
                    continue;   // canceled card approvals are not attachable evidence
                }
                candidates.add(trimReceipt(r));
                if (candidates.size() >= 10) {   // show up to 10 rows
                    break;
                }
            }
        }
        state.set("receiptCandidates", candidates);
        state.put("stage", "AWAIT_EVIDENCE_PICK");
        if (candidates.isEmpty()) {
            reply.append(t(ko,
                    "No unattached card receipts between " + start + " and " + end
                            + " for those card types. Try other card types, or add a manual expense. ",
                    start + " ~ " + end + " 기간에 해당 카드의 미첨부 사용 내역이 없습니다. "
                            + "다른 카드로 조회하거나, 경비를 직접 입력할 수 있어요. "));
            // Stay on card-type selection — the period is already set, so don't re-ask the date.
            state.put("stage", "AWAIT_CARD_TYPES");
            List<TripPlanAgentResponse.PendingChoice> chips = new ArrayList<>(cardTypeChips(ko));
            chips.add(TripPlanAgentResponse.PendingChoice.builder()
                    .kind("MANUAL_EXPENSE").name(t(ko, "no receipt?", "영수증이 없나요?"))
                    .options(List.of(TripPlanAgentResponse.Option.builder()
                            .label(t(ko, "Add manual expense", "직접 경비 입력"))
                            .sendText("manual-expense").build()))
                    .build());
            return chips;
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
                        + "). The draft is ready — submit it to BizPlay to finish. ",
                "\"" + doc.path("title").asText("") + "\" 정산서: 증빙 " + n
                        + "건, 총액 ₩" + doc.path("totalBstrAmount").asText("0")
                        + " (개인 정산 ₩" + doc.path("totalSettleAmount").asText("0")
                        + "). 정산서 초안이 준비되었습니다 — BizPlay에 제출하면 완료됩니다. "));
    }

    // --- chips -------------------------------------------------------------------

    private List<TripPlanAgentResponse.PendingChoice> planChipsFromState(ObjectNode state, boolean ko) {
        ArrayNode candidates = (ArrayNode) state.withArray("planCandidates");
        if (candidates.isEmpty()) {
            return null;
        }
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (JsonNode c : candidates) {
            // label = the one-line form (old clients / plain-text log); meta = the columns the
            // chat UI lays out as a table: 목적 · 제목 · 문서번호 · 기간 · 기안자 · 등록자.
            java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
            meta.put("purpose", c.path("purpose").asText(""));
            meta.put("title", c.path("title").asText(""));
            meta.put("docNo", c.path("docNo").asText(""));
            meta.put("startDate", c.path("startDate").asText(""));
            meta.put("endDate", c.path("endDate").asText(""));
            meta.put("drafter", c.path("drafter").asText(""));
            meta.put("registrar", c.path("registrar").asText(""));
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(c.path("docNo").asText("") + " · " + c.path("title").asText("")
                            + " · " + c.path("startDate").asText("") + "~" + c.path("endDate").asText("")
                            + (c.path("purpose").asText("").isEmpty() ? "" : " · " + c.path("purpose").asText("")))
                    .sendText("settle-plan:" + c.path("approvalId").asLong())
                    .meta(meta)
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
                // 기타증빙 (ETC): where manually-registered / etc-card receipts live — the ones the
                // settlement flow creates. Without this, picking any card type misses them entirely.
                TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Other receipts (기타증빙)", "기타증빙")).sendText("card-types:ETC").build(),
                TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "All", "전체"))
                        .sendText("card-types:CORP,PERSONAL,MY_DATA,ETC").build());
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("CARD_TYPE").name(t(ko, "card types", "카드 종류")).options(options).build());
    }

    /** After import: TranKind chips when the plan carries them, else jump to card-type selection. */
    private List<TripPlanAgentResponse.PendingChoice> afterImportChips(ObjectNode state, boolean ko) {
        return slots(state).withArray("planTranKinds").isEmpty()
                ? cardTypeChips(ko) : tranKindChips(state, ko);
    }

    /** One chip per allowed TranKind (sendText = trankind:{id}); null when the plan has none. */
    private List<TripPlanAgentResponse.PendingChoice> tranKindChips(ObjectNode state, boolean ko) {
        ArrayNode tks = (ArrayNode) slots(state).withArray("planTranKinds");
        if (tks.isEmpty()) {
            return null;
        }
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (JsonNode tk : tks) {
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(tk.path("name").asText("TranKind " + tk.path("id").asLong()))
                    .sendText("trankind:" + tk.path("id").asLong())
                    .build());
        }
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("TRANKIND").name(t(ko, "expense type", "경비 항목")).options(options).build());
    }

    private boolean hasTranKind(ObjectNode state, long id) {
        for (JsonNode tk : slots(state).withArray("planTranKinds")) {
            if (tk.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    /** A plan TranKind whose name the message mentions ("register 숙박비") — null when none. */
    private Long matchTranKindByName(ObjectNode state, String message) {
        if (message == null) {
            return null;
        }
        for (JsonNode tk : slots(state).withArray("planTranKinds")) {
            String name = tk.path("name").asText("");
            if (!name.isBlank() && message.contains(name)) {
                return tk.path("id").asLong();
            }
        }
        return null;
    }

    private boolean wantsRegister(String m) {
        return m != null && m.matches("(?ius).*(register|add|enter|입력|등록|추가|작성|하고\\s*싶|할래|할게).*");
    }

    /** After an expense is added: the plan's TranKind chips (register another) + a Done chip. */
    private List<TripPlanAgentResponse.PendingChoice> addAnotherChips(ObjectNode state, boolean ko) {
        List<TripPlanAgentResponse.PendingChoice> chips = new ArrayList<>();
        List<TripPlanAgentResponse.PendingChoice> tk = tranKindChips(state, ko);
        if (tk != null) {
            chips.addAll(tk);
        }
        chips.add(TripPlanAgentResponse.PendingChoice.builder()
                .kind("DONE").name(t(ko, "finish", "완료"))
                .options(List.of(TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Done — no more expenses", "완료 — 더 없음"))
                        .sendText("receipts-done").build()))
                .build());
        return chips;
    }

    /** Remember the picked TranKind (id + type + name) — drives the receipt search and manual entry. */
    private void selectTranKind(ObjectNode state, long tranKindId) {
        ObjectNode slots = slots(state);
        slots.put("evidenceTranKindId", tranKindId);
        for (JsonNode tk : slots.withArray("planTranKinds")) {
            if (tk.path("id").asLong() == tranKindId) {
                slots.put("evidenceTranKindName", tk.path("name").asText(""));
                if (tk.hasNonNull("type")) {
                    slots.put("evidenceTranKindType", tk.path("type").asText());
                } else {
                    slots.remove("evidenceTranKindType");
                }
                return;
            }
        }
    }

    /** The end-of-flow "submit to BizPlay" chip — clicking it sends the deterministic `submit` token. */
    private List<TripPlanAgentResponse.PendingChoice> submitChips(boolean ko) {
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("SUBMIT").name(t(ko, "submit", "제출"))
                .options(List.of(TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Submit to BizPlay", "BizPlay에 제출"))
                        .sendText("submit").build()))
                .build());
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
        // No matching receipt? Enter the expense manually (etc-card + required image).
        options.add(TripPlanAgentResponse.Option.builder()
                .label(t(ko, "Add manual expense", "직접 경비 입력")).sendText("manual-expense").build());
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
        if (s.contains("기타") || s.contains("etc") || s.contains("other")) {
            types.add("ETC");   // 기타증빙 — where manual/etc-card receipts live
        }
        if (s.contains("전체") || s.contains("all")) {
            return List.of("CORP", "PERSONAL", "MY_DATA", "ETC");
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

    // --- TranKind (slide-7 hardcoded type → detail fields) -----------------------

    /** TranKind type → the extra ReceiptEtcDto detail fields it needs (slide 7; no endpoint). */
    private static final java.util.Map<String, java.util.List<String>> TRANKIND_DETAIL_FIELDS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("TRANSPORT", java.util.List.of("usedStartDate", "usedEndDate",
                            "vehicleType", "routeType", "seatClass", "departTerminalId",
                            "arrivalTerminalId", "depart", "arrival")),
                    java.util.Map.entry("HD_TRANSPORT", java.util.List.of("usedStartDate", "usedEndDate",
                            "vehicleType", "routeType", "seatClass", "departTerminalId",
                            "arrivalTerminalId", "depart", "arrival")),
                    java.util.Map.entry("ROOM", java.util.List.of("usedStartDate", "usedEndDate",
                            "starRating", "roomType", "partnerHotel")),
                    java.util.Map.entry("HD_ROOM", java.util.List.of("usedStartDate", "usedEndDate",
                            "starRating", "roomType", "partnerHotel")),
                    java.util.Map.entry("FOOD", java.util.List.of("usedStartDate", "usedEndDate",
                            "foodDivisionType", "personCount")),
                    java.util.Map.entry("HD_FOOD", java.util.List.of("usedStartDate", "usedEndDate",
                            "foodDivisionType", "personCount")));

    /** used-dates only by default (TOLL, DAILY_COST, misc). */
    private static final java.util.List<String> DEFAULT_DETAIL_FIELDS =
            java.util.List.of("usedStartDate", "usedEndDate");

    private java.util.List<String> detailFieldsForType(String tranKindType) {
        return TRANKIND_DETAIL_FIELDS.getOrDefault(
                tranKindType == null ? "" : tranKindType, DEFAULT_DETAIL_FIELDS);
    }

    /**
     * Extract the plan's allowed TranKind ids (paper.paperItemOrderDto[].itemDto.tranKinds) and
     * resolve each to {id, name, type} via the TranKind master. Stored in slots.planTranKinds for the
     * "find receipts by TranKind" chips + manual entry. Best-effort — a lookup hiccup leaves the ids
     * without name/type.
     */
    private void resolvePlanTranKinds(ObjectNode state, JsonNode planDetail, String token, List<String> subAgents) {
        // The plan-detail tranKinds are OBJECTS {id, type, name, ...} — use them directly. (Bare-id
        // arrays are handled as a fallback via the TranKind master.)
        java.util.LinkedHashMap<Long, ObjectNode> byId = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<Long> needMaster = new java.util.LinkedHashSet<>();
        for (JsonNode item : planDetail.path("paper").path("paperItemOrderDto")) {
            for (JsonNode tk : item.path("itemDto").path("tranKinds")) {
                if (tk.isObject() && tk.hasNonNull("id")) {
                    long id = tk.path("id").asLong();
                    if (byId.containsKey(id)) {
                        continue;
                    }
                    ObjectNode o = objectMapper.createObjectNode();
                    o.put("id", id);
                    o.put("name", tk.path("name").asText("TranKind " + id));
                    if (tk.hasNonNull("type")) {
                        o.put("type", tk.path("type").asText());
                    } else {
                        o.putNull("type");
                    }
                    byId.put(id, o);
                } else if (tk.canConvertToLong()) {
                    needMaster.add(tk.asLong());
                }
            }
        }
        if (byId.isEmpty() && needMaster.isEmpty()) {
            return;
        }
        // Resolve any bare ids from the master (only when the plan gave ids, not objects).
        if (!needMaster.isEmpty()) {
            try {
                java.util.Map<Long, JsonNode> master = new java.util.HashMap<>();
                for (JsonNode tk : bizplayGatewayService.getTranKindList(token)) {
                    master.put(tk.path("id").asLong(), tk);
                }
                for (Long id : needMaster) {
                    if (byId.containsKey(id)) {
                        continue;
                    }
                    JsonNode m = master.get(id);
                    ObjectNode o = objectMapper.createObjectNode();
                    o.put("id", id);
                    o.put("name", m == null ? ("TranKind " + id) : m.path("name").asText("TranKind " + id));
                    if (m != null && m.hasNonNull("type")) {
                        o.put("type", m.path("type").asText());
                    } else {
                        o.putNull("type");
                    }
                    byId.put(id, o);
                }
            } catch (RuntimeException e) {
                log.warn("TranKind master lookup failed: {}", e.getMessage());
            }
        }
        ArrayNode arr = slots(state).putArray("planTranKinds");
        byId.values().forEach(arr::add);
        if (!subAgents.contains("TRANKIND_TOOL")) {
            subAgents.add("TRANKIND_TOOL");
        }
    }

    // --- slots (the session's data in hand) --------------------------------------

    /** The slot bag under agent_state.slots — created on first access. */
    private ObjectNode slots(ObjectNode state) {
        JsonNode s = state.get("slots");
        return (s instanceof ObjectNode o) ? o : state.putObject("slots");
    }

    /** travelerId == corpUserId == the configured static demo user. Never asked. */
    private void seedStaticUser(ObjectNode state) {
        ObjectNode s = slots(state);
        String u = bizplayProperties.getDefaultCorpUserId();
        s.put("travelerId", u);
        s.put("corpUserId", u);
    }

    /** The search window from the slot bag, or null when it isn't set yet. */
    private String[] slotPeriod(ObjectNode state) {
        ObjectNode s = slots(state);
        if (s.hasNonNull("startDate") && s.hasNonNull("endDate")) {
            return new String[]{s.get("startDate").asText(), s.get("endDate").asText()};
        }
        return null;
    }

    private List<String> cardTypesFromSlots(ObjectNode state) {
        List<String> types = new ArrayList<>();
        for (JsonNode c : slots(state).path("cardTypes")) {
            types.add(c.asText());
        }
        return types;
    }

    /**
     * Fill the slot bag from a free-text turn: the deterministic parsers first (reliable — they own
     * relative dates and Korean), then merge the slot-filler's LLM extraction for anything they
     * missed, so one message can populate several slots at once. Only fills empty slots — never
     * overwrites a value already in hand.
     */
    private void gatherIntoSlots(ObjectNode state, String message, JsonNode extracted) {
        ObjectNode s = slots(state);
        String[] period = extractPeriod(message);
        if (period != null) {
            s.put("startDate", period[0]);
            s.put("endDate", period[1]);
        }
        List<String> words = parseCardWords(message);
        if (!words.isEmpty()) {
            ArrayNode ct = s.putArray("cardTypes");
            words.forEach(ct::add);
        }
        mergeExtracted(s, extracted);
    }

    /** Merge the LLM-extracted slots, filling only what the deterministic parsers left empty. */
    private void mergeExtracted(ObjectNode s, JsonNode ex) {
        if (ex == null || !ex.isObject()) {
            return;
        }
        if (!s.hasNonNull("startDate") && ex.hasNonNull("startDate") && ex.hasNonNull("endDate")) {
            s.put("startDate", ex.get("startDate").asText());
            s.put("endDate", ex.get("endDate").asText());
        }
        if ((!s.has("cardTypes") || s.path("cardTypes").isEmpty()) && ex.path("cardTypes").isArray()) {
            ArrayNode ct = objectMapper.createArrayNode();
            for (JsonNode c : ex.get("cardTypes")) {
                String norm = normalizeCard(c.asText(""));
                if (norm != null && !ct.toString().contains("\"" + norm + "\"")) {
                    ct.add(norm);
                }
            }
            if (!ct.isEmpty()) {
                s.set("cardTypes", ct);
            }
        }
        if (!s.hasNonNull("planHint") && ex.hasNonNull("planHint") && !ex.get("planHint").asText().isBlank()) {
            s.put("planHint", ex.get("planHint").asText().trim());
        }
    }

    private String normalizeCard(String raw) {
        return switch (raw == null ? "" : raw.trim().toUpperCase()) {
            case "CORP", "PERSONAL", "MY_DATA" -> raw.trim().toUpperCase();
            default -> null;
        };
    }

    /**
     * Ask-only-missing gate: the required slot keys the given endpoint still lacks. Empty = all
     * required params are in hand, so the endpoint can run without asking anything.
     */
    private List<String> missingSlots(String endpoint, ObjectNode state) {
        ObjectNode s = slots(state);
        List<String> missing = new ArrayList<>();
        for (String key : ENDPOINT_REQUIRED.getOrDefault(endpoint, List.of())) {
            JsonNode v = s.get(key);
            boolean present = v != null && !v.isNull()
                    && !(v.isTextual() && v.asText().isBlank())
                    && !(v.isArray() && v.isEmpty());
            if (!present) {
                missing.add(key);
            }
        }
        return missing;
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

    /**
     * A clear request to SUBMIT the settlement at the end — the "submit" chip or an explicit
     * save/submit phrase. Kept narrow to avoid firing on "첨부 완료" (that's receipts-done) or on
     * mid-flow chatter; the actual provider POST only runs once a plan is imported.
     */
    private boolean isSubmitRequest(String m) {
        if (m == null || m.isBlank() || m.length() > 100) {
            return false;
        }
        return m.matches("(?i)\\s*submit\\b.*")
                || m.matches("(?ius).*(submit|finali[sz]e|save the settlement|post it to bizplay|"
                        + "제출|상신|결재.?올려|정산서.*(제출|올려)|비즈플레이.*(제출|올려)).*");
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
