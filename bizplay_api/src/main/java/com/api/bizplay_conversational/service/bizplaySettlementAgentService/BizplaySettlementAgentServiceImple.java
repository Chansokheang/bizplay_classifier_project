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
    private static final Pattern APPROVER_PICK = Pattern.compile("(?i)\\s*approver:(\\d+).*");
    private static final Pattern TRANKIND_PICK = Pattern.compile("(?i).*trankind:(\\d+).*");
    private static final Pattern RECEIPTS_DONE = Pattern.compile("(?i).*(receipts-done|첨부 ?완료|evidence done).*");
    /** Receipts shown as chips per page — the rest stay listed in the reply text. */
    private static final int CHIP_LIMIT = 12;
    /** How many rows to read before filtering. Big enough that the window, not the cap,
     *  decides what is considered - the cap only decides what is DISPLAYED. */
    private static final int SCAN_LIMIT = 500;
    /**
     * The two plan states this flow cares about (BizPlay's approvalStatusType).
     * APPROVED = an approver signed it, so a settlement may ride it. DRAFTED = filed but not yet
     * approved — BizPlay will not accept it as a settlement anchor, so the agent shows those
     * plans only to explain WHY they cannot be settled yet.
     */
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_DRAFTED = "DRAFTED";
    /** The plan-search window used when the user names no period. */
    private static final int DEFAULT_SEARCH_MONTHS = 1;
    /** The settlement chat OPENS on this window: the trips just finished are the ones to settle. */
    /** RETIRED (kept for reference, do not delete): the opening window was a week either side
     *  of today. It hid approved-but-unsettled trips older than that - the one thing the
     *  settlement opener exists to surface - so the opener now scans WIDE_SEARCH_MONTHS. */
    @SuppressWarnings("unused")
    private static final int OPENING_SEARCH_DAYS = 7;
    /** How far the search widens when the default window turns up no approved plan. */
    private static final int WIDE_SEARCH_MONTHS = 6;
    /** Chip token for the pending-plans tool, so the UI can offer it without phrasing anything. */
    private static final Pattern PENDING_PLANS_TOKEN = Pattern.compile("(?i)\\s*pending-plans\\s*");
    /** Disambiguated stop pick: stop:from|to:{nodeId}. */
    private static final Pattern STOP_PICK = Pattern.compile("(?i)\\s*stop:(from|to):([A-Za-z0-9_-]+).*");
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
            "planHint", "any words identifying WHICH trip to settle (destination, purpose, or doc no)",
            // Read as an INTENT for this turn only (never stored in the slot bag): the LLM decides
            // whether the user is asking about approval status rather than asking to settle.
            "pendingPlans", "true ONLY when the user asks to SEE trip-plan requests that are still "
                    + "waiting for approval / not yet approved / pending an approver — not when they "
                    + "ask to settle a trip. Omit the field otherwise");

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
        String rawMessage = request.getMessage() == null ? "" : request.getMessage().trim();
        if (rawMessage.isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }
        boolean ko = koreanConversation(rawMessage);
        // The language marker has done its job — drop it before anything reads the message as
        // content (slot extraction, plan hints, the stage tokens), or "Respond in Korean only"
        // ends up being searched for as a trip name. (Kept effectively final: the guardrail and
        // slot-filler run in lambdas that capture it.)
        String message = rawMessage.replaceFirst(
                "(?i)^\\(\\s*respond in (korean|english) only[^)]*\\)\\s*", "").trim();

        // Stage-machine chip tokens (settle-plan:{id}, evidence-period:default, receipt:all …)
        // are generated by our own UI and deterministic — never send them through the LLM
        // guardrail, which can misread a bare machine token as an injection attempt.
        boolean machineToken = message.matches(
                "(?i)\\s*(settle-plan:|card-types:|receipt:|receipts-done|evidence-period:|manual-expense"
                        + "|submit|trankind:|expense-confirm|expense-cancel|approver:|pending-plans|settle-start|stop:).*");
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

        // TOOL: which of my request plans are still waiting for approval? Answered from the
        // UNSCOPED plan list (the scoped one only ever returns APPROVED rows). It is a lookup,
        // not a step of the settlement — the stage machine is left exactly where it was.
        // Triggered by the UI's own chip token, or by the LLM reading the user's own words.
        boolean pendingAsk = PENDING_PLANS_TOKEN.matcher(message).matches()
                || (extractedSlots != null && extractedSlots.path("pendingPlans").asBoolean(false));
        if (pendingAsk) {
            StringBuilder pendingReply = new StringBuilder();
            List<String> pendingAgents = new ArrayList<>();
            List<TripPlanAgentResponse.PendingChoice> pendingChips = pendingPlans(
                    state, Long.parseLong(request.getCorpUserId().trim()), bizplayToken,
                    pendingAgents, pendingReply, ko);
            appendTurn(session, "user", message);
            appendTurn(session, "assistant", pendingReply.toString());
            saveState(session, state);
            ConversationalAgentSession savedPending = sessionRepo.save(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(savedPending.getId().toString())
                    .status(savedPending.getStatus() == null ? null : savedPending.getStatus().name())
                    .intent("PENDING_PLANS")
                    .subAgents(pendingAgents)
                    .reply(pendingReply.toString().trim())
                    .pendingChoices(pendingChips)
                    .draftJson(savedPending.getDraftJson())
                    .build();
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
            // DEMO SCOPE: PARTIAL registration is switched off — every manual expense is registered
            // COMPLETE (one etc-card POST with the details). The partial path is commented out
            // below (kept for reference, do not delete), so a bare "manual-expense" no longer asks
            // which way to register: it opens the complete form directly.
            // boolean partial = message.matches("(?i).*manual-expense:partial.*");
            boolean partial = false;
            boolean complete = true;   // was: message.matches("(?i).*manual-expense:complete.*");
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

        // Approver pick from the chip: remember it on the session, then offer submit again.
        java.util.regex.Matcher approverPick = APPROVER_PICK.matcher(message);
        if (approverPick.matches() && !documents.isEmpty()) {
            long approverId = Long.parseLong(approverPick.group(1));
            slots(state).put("approverId", approverId);
            String who = approverName(approverId, bizplayToken);
            String reply = t(ko,
                    (who.isEmpty() ? "Approver set." : who + " will approve this settlement.")
                            + " Submit it to BizPlay when you're ready.",
                    (who.isEmpty() ? "결재자를 지정했어요." : who + " 님이 이 정산서를 결재합니다.")
                            + " 준비되면 BizPlay에 제출할게요.");
            appendTurn(session, "user", message);
            appendTurn(session, "assistant", reply);
            saveState(session, state);
            ConversationalAgentSession savedAp = sessionRepo.save(session);
            return BizplayPlanAgentResponse.builder()
                    .sessionId(savedAp.getId().toString())
                    .status(savedAp.getStatus() == null ? null : savedAp.getStatus().name())
                    .intent("APPROVER_PICKED")
                    .subAgents(List.of("SETTLEMENT_AGENT"))
                    .reply(reply)
                    .pendingChoices(submitChips(ko, bizplayToken))
                    .draftJson(savedAp.getDraftJson())
                    .build();
        }

        // ⑦ Final submit — only when the USER asks to (a "submit" chip or "제출/save" in words), and
        // only once a plan has been imported (a real draft exists). POSTs the finished draft to
        // BizPlay's own /bstr/report/draft. Approver picks are handled by the separate create
        // endpoint; a chat submit carries just the drafter's DRAFT line.
        if (!documents.isEmpty() && isSubmitRequest(message)
                && state.path("anchor").hasNonNull("approvalId")) {
            if (!hasEvidence(documents)) {
                // Refuse rather than file a ₩0 document nobody can act on.
                String empty = t(ko,
                        "There's no expense on this settlement yet — attach a receipt or register "
                                + "the expense manually, then submit. ",
                        "이 정산서에는 아직 경비가 없어요 — 증빙을 첨부하거나 경비를 직접 등록한 뒤 "
                                + "제출해 주세요. ");
                appendTurn(session, "user", message);
                appendTurn(session, "assistant", empty);
                saveState(session, state);
                ConversationalAgentSession savedEmpty = sessionRepo.save(session);
                return BizplayPlanAgentResponse.builder()
                        .sessionId(savedEmpty.getId().toString())
                        .status(savedEmpty.getStatus() == null ? null : savedEmpty.getStatus().name())
                        .intent("EVIDENCE_PICK_PENDING")
                        .subAgents(List.of("SETTLEMENT_AGENT"))
                        .reply(empty)
                        .pendingChoices(receiptChips(state, documents, ko))
                        .draftJson(savedEmpty.getDraftJson())
                        .build();
            }
            sanitizeEtcSaveRequests(documents, bizplayToken, slots(state).path("approverId").asLong(0));
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
            if (!hasEvidence(documents)) {
                // Nothing attached: finishing here would offer a submit that can only file a ₩0
                // document. An attach may have been refused (a receipt with no ISSUED child)
                // without the user registering it, so say what is missing and stay on this step.
                String empty = t(ko,
                        "There's nothing on this settlement yet, so there's nothing to submit. "
                                + "Attach a receipt, or register the expense manually. ",
                        "아직 이 정산서에 첨부된 경비가 없어서 제출할 수 없어요. 증빙을 첨부하시거나 "
                                + "경비를 직접 등록해 주세요. ");
                appendTurn(session, "user", message);
                appendTurn(session, "assistant", empty);
                saveState(session, state);
                ConversationalAgentSession savedEmptyDone = sessionRepo.save(session);
                return BizplayPlanAgentResponse.builder()
                        .sessionId(savedEmptyDone.getId().toString())
                        .status(savedEmptyDone.getStatus() == null ? null : savedEmptyDone.getStatus().name())
                        .intent("EVIDENCE_PICK_PENDING")
                        .subAgents(List.of("SETTLEMENT_AGENT"))
                        .reply(empty)
                        .pendingChoices(receiptChips(state, documents, ko))
                        .draftJson(savedEmptyDone.getDraftJson())
                        .build();
            }
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

        // ⑧ Conversational receipt registration: "I went last Tuesday by KTX from Seoul Station to
        // Busan Station" fills the etc-card body from the sentence, asks ONE question per missing
        // required field, then PREVIEWS the body for the user to confirm before the POST runs.
        // The register form remains the other way in — this path never opens it.
        if (state.path("anchor").hasNonNull("approvalId") && !documents.isEmpty()) {
            BizplayPlanAgentResponse conv =
                    conversationalExpenseTurn(session, state, documents, message, machineToken,
                            request.getCorpNo(), bizplayToken, ko);
            if (conv != null) {
                return conv;
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
                        if (chips == null) {
                            intent = "AWAIT_PLAN_PERIOD";   // nothing found -> offer the calendar
                        }
                    } else {
                        // Nothing fetched yet (the user opened with a trip name, not a period) —
                        // search by that name over the recent months before trying to pick.
                        String hint = slots(state).path("planHint").asText(message);
                        if (state.withArray("planCandidates").isEmpty() && !hintWords(hint).isEmpty()) {
                            intent = "PLAN_SEARCH";
                            chips = searchPlansByName(state, corpUserId, hint, bizplayToken,
                                    subAgents, reply, ko);
                            break;
                        }
                        // Sub-agent: Plan Picker — "the Busan education trip" resolves against
                        // the fetched candidates (deterministic match first, LLM on the miss).
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
                if (RECEIPTS_DONE.matcher(message).matches() && !hasEvidence(documents)) {
                    // "Done" with nothing attached would file a ₩0 settlement. Stay on this step
                    // and say what is missing — an attach can have been refused (a receipt with no
                    // ISSUED child) without the user noticing.
                    intent = "EVIDENCE_PICK_PENDING";
                    reply.append(t(ko,
                            "There's nothing on this settlement yet, so there is nothing to submit. "
                                    + "Attach a receipt, or register the expense manually. ",
                            "아직 이 정산서에 첨부된 경비가 없어서 제출할 수 없어요. 증빙을 첨부하시거나 "
                                    + "경비를 직접 등록해 주세요. "));
                    chips = receiptChips(state, documents, ko);
                } else if (RECEIPTS_DONE.matcher(message).matches()) {
                    intent = "SETTLEMENT_READY";
                    state.put("stage", "DONE");
                    session.setStatus(ConversationalAgentSession.AgentStatus.READY_FOR_REVIEW);
                    summarize(documents, reply, ko);
                    chips = submitChips(ko);   // the draft is ready — offer to submit it to BizPlay
                } else if (m.matches()) {
                    intent = "EVIDENCE_ATTACH";
                    attachReceipts(state, documents, m.group(1), bizplayToken, reply, ko);
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
                if (hasEvidence(documents)) {
                    intent = "SETTLEMENT_READY";
                    summarize(documents, reply, ko);
                    chips = submitChips(ko);
                } else {
                    // Emptied out (or never filled) — don't keep offering a submit that can only
                    // produce a ₩0 document.
                    intent = "EVIDENCE_PICK_PENDING";
                    state.put("stage", "AWAIT_EVIDENCE_PICK");
                    reply.append(t(ko,
                            "This settlement has no expense on it yet — attach a receipt or register "
                                    + "the expense manually before submitting. ",
                            "이 정산서에는 아직 경비가 없어요 — 제출 전에 증빙을 첨부하거나 경비를 "
                                    + "직접 등록해 주세요. "));
                    chips = receiptChips(state, documents, ko);
                }
            }
            default -> {
                // New settlement conversation: resolve the plan-search window first — from the
                // slot bag, which the gather step just filled from this message (deterministic + LLM).
                String[] period = slotPeriod(state);
                String nameHint = slots(state).path("planHint").asText("");
                if (period != null) {
                    // Always list the matching plans for the user to pick — never auto-select one.
                    intent = "PLAN_SEARCH";
                    chips = searchPlans(state, corpUserId, period[0], period[1],
                            bizplayToken, subAgents, reply, ko);
                    if (chips == null) {
                        intent = "AWAIT_PLAN_PERIOD";   // nothing found -> offer the calendar
                    }
                } else if (!hintWords(nameHint).isEmpty()) {
                    // Named a trip but no period ("settle the KSHRD trip") — search by name over
                    // the recent months and answer with a preview / a table / a way forward.
                    intent = "PLAN_SEARCH";
                    chips = searchPlansByName(state, corpUserId, nameHint, bizplayToken,
                            subAgents, reply, ko);
                } else {
                    // Nothing named yet. Don't open with a question - open with the reminder:
                    // the approved plans of the last month, the ones that CAN be settled.
                    state.put("stage", "AWAIT_PLAN_PICK");   // next parsable period triggers the search
                    chips = openingApprovedPlans(state, corpUserId, bizplayToken, subAgents, reply, ko);
                    intent = chips == null ? "AWAIT_PERIOD" : "PLAN_SEARCH";
                    if (chips != null) {
                        break;
                    }
                    // Nothing approved and nothing pending: say what to do when no plan exists -
                    // a settlement can only ever ride an approved request plan.
                    reply.append(t(ko,
                            "Happy to settle a trip. Which one is it — the trip name, or the period "
                                    + "it falls in (e.g. 2026-07-06 ~ 2026-08-06, \"last month\")? "
                                    + "If the request plan (출장계획서) was never filed, create it on the "
                                    + "Request Plan page first and we'll settle it after approval. ",
                            "출장 정산을 도와드릴게요. 어떤 출장인가요 — 출장명이나 기간을 알려주세요 "
                                    + "(예: 2026-07-06 ~ 2026-08-06, \"지난달\"). "
                                    + "아직 출장계획서를 작성하지 않으셨다면 출장 신청 화면에서 먼저 "
                                    + "작성해 주세요 — 승인 후 정산할 수 있어요. "));
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
            if (!hasEvidence(documents)) {
                // The UI's Submit button lands here. An empty body would file a ₩0 settlement,
                // so the caller gets a message it can show instead of a silent success.
                throw new IllegalArgumentException(
                        "This settlement has no expense on it — attach a receipt or register the "
                                + "expense manually before submitting.");
            }
            ObjectNode state = loadState(session);
            boolean ko = "ko".equals(state.path("lang").asText(null));
            applyPickedApprovalLines((ObjectNode) documents.get(0), approvalLines);

            // POST the draft_json AS-IS — it already carries the 정산서 save-body structure.
            sanitizeEtcSaveRequests(documents, bizplayToken, slots(state).path("approverId").asLong(0));
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
            if (!base.hasNonNull("tranKindId")) {
                // A receipt POSTed with no tranKindId comes back NOT ISSUED and can never be
                // attached to a settlement — refuse rather than leave dead evidence behind.
                throw new IllegalArgumentException(
                        "Pick the expense type first — a receipt registered without one cannot be "
                                + "attached to a settlement.");
            }
            // Upload the (optional) image first so its id rides in the single etc-card body.
            long fileId = 0;
            if (image != null && image.length > 0) {
                fileId = bizplayGatewayService.uploadReceiptFile(image, filename, bizplayToken);
            }
            ObjectNode fullBody = buildEtcExpense(base, detail, fileId);   // base + TranKind + detail + imageIds
            long newReceiptId = createEtcReceipt(fullBody, bizplayToken);   // single POST, no PATCH

            ObjectNode entry = fullBody.deepCopy();
            if (newReceiptId > 0) {
                // The provider's documented image flow is upload -> PATCH /receipt/image/{id} with
                // a bare [fileId]. imageIds already rides in the create body above; this makes the
                // link the way their own client does it. Best-effort: the expense is registered
                // either way, and a missing image is better than losing the receipt.
                if (fileId > 0) {
                    try {
                        bizplayGatewayService.attachReceiptImages(
                                newReceiptId, java.util.List.of(fileId), bizplayToken);
                    } catch (RuntimeException e) {
                        log.warn("receipt image PATCH failed for {} — expense kept: {}",
                                newReceiptId, e.getMessage());
                    }
                }
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
    /** How far back a name-only search looks when the user names a trip but no period. */
    private static final int NAME_SEARCH_MONTHS = 2;

    /**
     * Words that say "settle a trip" rather than WHICH trip. The hint slot carries the user's
     * whole phrase, and "출장"/"trip" appears in nearly every plan title — matching on them
     * returns the entire list. Only the words left after these are distinguishing.
     */
    private static final java.util.Set<String> HINT_STOPWORDS = java.util.Set.of(
            "출장", "정산", "정산서", "계획", "계획서", "경비", "비용", "영수증", "해줘", "해주세요",
            "하고", "싶어", "싶어요", "주세요", "해라", "가자", "이번", "저번", "관련", "건",
            "settle", "settlement", "settling", "expense", "expenses", "report", "reports",
            "trip", "trips", "business", "plan", "plans", "trip's", "the", "my", "our", "a", "an",
            "for", "of", "to", "on", "in", "do", "does", "let", "lets", "let's", "want", "wants",
            "please", "would", "like", "i", "we", "id", "want to", "make", "create");

    /** The distinguishing words of a trip hint — stopwords and 1-char noise removed. */
    private List<String> hintWords(String hint) {
        List<String> words = new ArrayList<>();
        if (hint == null) {
            return words;
        }
        for (String raw : hint.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() >= 2 && !HINT_STOPWORDS.contains(raw)) {
                words.add(raw);
            }
        }
        return words;
    }

    /**
     * "Settle the KSHRD trip" — no period, just a trip. Searches the last
     * {@value #NAME_SEARCH_MONTHS} months and answers by how many plans match the name:
     * one -> preview it for confirmation; several -> the picker table; none -> ask for a
     * period or point at the Request Plan page. Returns null when the message names no trip.
     */
    private List<TripPlanAgentResponse.PendingChoice> searchPlansByName(
            ObjectNode state, long corpUserId, String hint, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        if (hint == null || hint.isBlank()) {
            return null;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        // Both directions: a plan is approved BEFORE the trip happens, so the trip a user asks to
        // settle sits as often just ahead of today as behind it. The scoped list is filtered
        // server-side, so an end date of "today" would simply hide those.
        String start = today.minusMonths(NAME_SEARCH_MONTHS).toString();
        String end = today.plusMonths(NAME_SEARCH_MONTHS).toString();
        JsonNode list = bizplayGatewayService.getPlanList(corpUserId, start, end, token);
        subAgents.add("PLAN_SEARCH_TOOL");
        ArrayNode inWindow = planCandidates(list, start, end, 500);
        if (inWindow.isEmpty()) {
            // Widen once rather than reporting "no such trip" through a two-month keyhole. The
            // provider honours the period, so a wider answer needs a wider ASK, not a re-filter.
            start = today.minusMonths(WIDE_SEARCH_MONTHS).toString();
            end = today.plusMonths(WIDE_SEARCH_MONTHS).toString();
            list = bizplayGatewayService.getPlanList(corpUserId, start, end, token);
            inWindow = planCandidates(list, start, end, 500);
        }
        // Score, don't just test: common words ("2026" in every docNo) would otherwise match
        // everything. Only the best-scoring trips are shown, so the distinguishing word wins.
        List<String> words = hintWords(hint);
        ArrayNode matches = objectMapper.createArrayNode();
        int best = 0;
        java.util.Map<Integer, Integer> scores = new java.util.HashMap<>();
        for (int i = 0; i < inWindow.size(); i++) {
            JsonNode c = inWindow.get(i);
            String haystack = (c.path("title").asText("") + " " + c.path("purpose").asText("")
                    + " " + c.path("docNo").asText("")).toLowerCase(java.util.Locale.ROOT);
            int score = 0;
            for (String word : words) {
                if (haystack.contains(word)) {
                    score++;
                }
            }
            scores.put(i, score);
            best = Math.max(best, score);
        }
        if (best > 0) {
            for (int i = 0; i < inWindow.size(); i++) {
                if (scores.get(i) == best) {
                    matches.add(inWindow.get(i));
                }
            }
        }
        if (matches.isEmpty()) {
            state.set("planCandidates", inWindow);
            state.put("stage", "AWAIT_PLAN_PICK");
            reply.append(t(ko,
                    "I couldn't find an approved trip plan matching \"" + hint + "\" between "
                            + start + " and " + end + ". Tell me the period to search instead — "
                            + "or, if the plan was never filed, create it first on the Request Plan page. "
                            + "A plan still waiting for approval can't be settled either — ask and "
                            + "I'll list those. ",
                    start + " ~ " + end + " 기간에서 \"" + hint
                            + "\"와(과) 일치하는 승인된 출장 계획을 찾지 못했어요. 조회할 기간을 알려주시거나, "
                            + "아직 계획서를 작성하지 않으셨다면 출장 신청 화면에서 먼저 작성해 주세요. "
                            + "승인 대기 중인 계획도 정산할 수 없어요 — 필요하시면 목록을 보여 드릴게요. "));
            return null;
        }
        state.set("planCandidates", matches);
        state.put("stage", "AWAIT_PLAN_PICK");
        if (matches.size() == 1) {
            JsonNode only = matches.get(0);
            reply.append(t(ko,
                    "I found this trip — settle it? ", "이 출장을 찾았어요 — 이걸로 정산할까요? "))
                    .append('\n')
                    .append(planSummaryLine(only, ko));
            return List.of(TripPlanAgentResponse.PendingChoice.builder()
                    .kind("PLAN").name(t(ko, "settle this trip?", "이 출장을 정산할까요?"))
                    .options(List.of(planOption(only, ko)))
                    .build());
        }
        reply.append(t(ko,
                "I found " + matches.size() + " trips matching \"" + hint + "\" — which one? ",
                "\"" + hint + "\"와(과) 일치하는 출장이 " + matches.size() + "건이에요 — 어떤 걸 정산할까요? "));
        return planChipsFromState(state, ko);
    }

    /**
     * The opening turn of a settlement chat, when the user named neither a period nor a trip.
     * Rather than asking for a period first, it REMINDS: the last {@value #DEFAULT_SEARCH_MONTHS}
     * month's plans that an approver has already signed ({@value #STATUS_APPROVED}) and can
     * therefore be settled - highlighting the ones with no settlement on them yet.
     * <p>Empty window -> widens to +/-{@value #WIDE_SEARCH_MONTHS} months and says so; still
     * empty -> falls through to the pending ({@value #STATUS_DRAFTED}) plans, which is usually
     * the real answer: the request exists but nobody has approved it yet.
     */
    private List<TripPlanAgentResponse.PendingChoice> openingApprovedPlans(
            ObjectNode state, long corpUserId, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        LocalDate today = LocalDate.now();
        // Six months either side, not a week. An approved plan nobody has settled is the one thing
        // this screen must never hide, and a narrow opening window did exactly that - a trip on
        // 08-10 simply did not appear, with nothing on screen to say it existed. The window reaches
        // forward as well as back because approval happens before the travel does.
        String start = today.minusMonths(WIDE_SEARCH_MONTHS).toString();
        String end = today.plusMonths(WIDE_SEARCH_MONTHS).toString();
        JsonNode list = bizplayGatewayService.getPlanList(corpUserId, start, end, token);
        subAgents.add("PLAN_SEARCH_TOOL");
        // Scan the whole window, THEN trim. Capping at CHIP_LIMIT before the unsettled filter let
        // already-settled plans spend the budget and push the unsettled ones off the end, which is
        // the opposite of what this screen is for.
        ArrayNode approved = planCandidates(list, start, end, SCAN_LIMIT, STATUS_APPROVED);
        if (approved.isEmpty()) {
            // No approved plan at all. The useful answer is what IS there: the requests still
            // sitting with an approver.
            return pendingPlans(state, corpUserId, token, subAgents, reply, ko);
        }
        // What the user came for: approved AND not yet settled. Plans that already carry a
        // settlement are legitimate targets too (companions, additional reports), so they are the
        // fallback rather than hidden outright.
        ArrayNode unsettled = unsettledOf(approved);
        boolean onlyUnsettled = !unsettled.isEmpty();
        ArrayNode all = onlyUnsettled ? unsettled : approved;
        int total = all.size();
        ArrayNode shown = trimTo(all, CHIP_LIMIT);
        state.set("planCandidates", shown);
        state.put("stage", "AWAIT_PLAN_PICK");

        if (onlyUnsettled) {
            reply.append(t(ko,
                    total + " approved trip plan(s) with no settlement yet. Pick one to settle it. ",
                    "승인됐지만 아직 정산하지 않은 출장 계획이 " + total + "건 있어요. 정산할 출장을 선택해 주세요. "));
        } else {
            reply.append(t(ko,
                    "Every approved plan already carries a settlement. You can still file another "
                            + "one on any of them — pick the trip to settle. ",
                    "승인된 계획은 모두 이미 정산서가 있어요. 추가 작성도 가능하니 정산할 출장을 선택해 주세요. "));
        }
        if (total > shown.size()) {
            reply.append(t(ko,
                    "Showing the " + shown.size() + " most recent — use the calendar for an older period. ",
                    "최근 " + shown.size() + "건만 표시했어요 — 더 이전 기간은 아래 달력에서 선택해 주세요. "));
        }
        return planChipsFromState(state, ko);
    }

    /**
     * TOOL - the traveler's request plans that are still waiting for an approver
     * ({@value #STATUS_DRAFTED}). BizPlay refuses a settlement whose anchor plan is not approved,
     * so these are listed to explain the block, never as anchors: the rows carry no
     * settle-plan token.
     * <p>Window: the period the user gave (this turn or earlier in the session), else the last
     * {@value #DEFAULT_SEARCH_MONTHS} month. The unscoped endpoint ignores the period query and
     * repeats a plan per approval line, so the window and the dedupe are applied here.
     */
    private List<TripPlanAgentResponse.PendingChoice> pendingPlans(
            ObjectNode state, long corpUserId, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        String[] period = slotPeriod(state);
        LocalDate today = LocalDate.now();
        String start = period != null ? period[0] : today.minusMonths(DEFAULT_SEARCH_MONTHS).toString();
        String end = period != null ? period[1] : today.toString();
        JsonNode list = bizplayGatewayService.getPendingPlanList(corpUserId, start, end, token);
        subAgents.add("PENDING_PLAN_TOOL");

        ArrayNode inWindow = planCandidates(list, start, end, 1000, STATUS_DRAFTED);
        ArrayNode everything = planCandidates(list, null, null, 5000, STATUS_DRAFTED);
        if (inWindow.isEmpty()) {
            if (everything.isEmpty()) {
                reply.append(t(ko,
                        "Nothing is waiting for approval - every trip plan I can see has been "
                                + "approved already. ",
                        "\uC2B9\uC778 \uB300\uAE30 \uC911\uC778 \uCD9C\uC7A5 \uACC4\uD68D\uC740 \uC5C6\uC5B4\uC694 - \uC870\uD68C\uB418\uB294 \uACC4\uD68D\uC740 \uBAA8\uB450 \uC2B9\uC778\uB41C \uC0C1\uD0DC\uC785\uB2C8\uB2E4. "));
                return null;
            }
            String[] span = spanOf(everything);
            reply.append(t(ko,
                    "No plan is waiting for approval between " + start + " and " + end
                            + ", but " + everything.size() + " are pending elsewhere ("
                            + span[0] + " ~ " + span[1] + "). Give me that period and I'll list them. ",
                    start + " ~ " + end + " \uAE30\uAC04\uC5D0\uB294 \uC2B9\uC778 \uB300\uAE30 \uC911\uC778 \uACC4\uD68D\uC774 \uC5C6\uC9C0\uB9CC, \uB2E4\uB978 \uAE30\uAC04\uC5D0 "
                            + everything.size() + "\uAC74\uC774 \uB300\uAE30 \uC911\uC774\uC5D0\uC694 (" + span[0] + " ~ " + span[1]
                            + "). \uD574\uB2F9 \uAE30\uAC04\uC744 \uC54C\uB824 \uC8FC\uC2DC\uBA74 \uBCF4\uC5EC \uB4DC\uB9B4\uAC8C\uC694. "));
            return null;
        }
        // Chronological, nearest trip first - a pending approval matters most when the trip is soon.
        List<JsonNode> sorted = new ArrayList<>();
        inWindow.forEach(sorted::add);
        sorted.sort(java.util.Comparator.comparing(n -> n.path("startDate").asText("")));
        int shown = Math.min(sorted.size(), CHIP_LIMIT);
        ArrayNode display = objectMapper.createArrayNode();
        sorted.subList(0, shown).forEach(display::add);

        reply.append(t(ko,
                inWindow.size() + " trip plan(s) between " + start + " and " + end
                        + " are still waiting for approval"
                        + (shown < inWindow.size() ? " (showing the first " + shown + ")" : "") + ". ",
                start + " ~ " + end + " \uAE30\uAC04\uC5D0 \uC2B9\uC778 \uB300\uAE30 \uC911\uC778 \uCD9C\uC7A5 \uACC4\uD68D\uC774 " + inWindow.size() + "\uAC74\uC774\uC5D0\uC694"
                        + (shown < inWindow.size() ? " (\uC55E\uC758 " + shown + "\uAC74\uB9CC \uD45C\uC2DC)" : "") + ". "));
        reply.append(t(ko,
                "They can't be settled yet - BizPlay only accepts an approved plan as the "
                        + "anchor of a settlement, so ask the approver to sign them first. ",
                "\uC774 \uACC4\uD68D\uB4E4\uC740 \uC544\uC9C1 \uC815\uC0B0\uD560 \uC218 \uC5C6\uC5B4\uC694 - \uC815\uC0B0\uC11C\uB294 \uC2B9\uC778\uB41C \uCD9C\uC7A5\uACC4\uD68D\uC11C\uC5D0\uB9CC \uC791\uC131\uD560 \uC218 \uC788\uC73C\uB2C8 "
                        + "\uACB0\uC7AC\uC790\uC5D0\uAC8C \uC2B9\uC778\uC744 \uC694\uCCAD\uD574 \uC8FC\uC138\uC694. "));
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("PLAN_PENDING")
                .name(t(ko, "waiting for approval", "\uC2B9\uC778 \uB300\uAE30 \uC911"))
                .options(pendingOptions(display, ko))
                .build());
    }

    /** Earliest..latest trip start date across a candidate list - for "pending elsewhere" replies. */
    private String[] spanOf(ArrayNode candidates) {
        String min = null;
        String max = null;
        for (JsonNode c : candidates) {
            String from = c.path("startDate").asText("");
            if (from.isEmpty()) {
                continue;
            }
            min = (min == null || from.compareTo(min) < 0) ? from : min;
            max = (max == null || from.compareTo(max) > 0) ? from : max;
        }
        return new String[]{min == null ? "?" : min, max == null ? "?" : max};
    }

    /**
     * Pending plans as display-only options: the picker's meta columns plus the approval state,
     * and NO sendText - clicking one must not start a settlement that BizPlay would refuse.
     */
    private List<TripPlanAgentResponse.Option> pendingOptions(ArrayNode candidates, boolean ko) {
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (JsonNode c : candidates) {
            java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
            meta.put("purpose", c.path("purpose").asText(""));
            meta.put("title", c.path("title").asText(""));
            meta.put("docNo", c.path("docNo").asText(""));
            meta.put("startDate", c.path("startDate").asText(""));
            meta.put("endDate", c.path("endDate").asText(""));
            meta.put("drafter", c.path("drafter").asText(""));
            meta.put("created", c.path("created").asText(""));
            meta.put("status", t(ko, "Waiting for approval", "\uC2B9\uC778 \uB300\uAE30"));
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(c.path("docNo").asText("") + " \u00B7 " + c.path("title").asText("")
                            + " \u00B7 " + c.path("startDate").asText("") + "~" + c.path("endDate").asText(""))
                    .meta(meta)
                    .build());
        }
        return options;
    }

    /** One plan, written out for the user to check before importing it. */
    private String planSummaryLine(JsonNode c, boolean ko) {
        return "· " + t(ko, "title", "제목") + ": " + c.path("title").asText("-") + "\n"
                + "· " + t(ko, "document", "문서번호") + ": " + c.path("docNo").asText("-") + "\n"
                + "· " + t(ko, "period", "기간") + ": " + c.path("startDate").asText("-")
                + " ~ " + c.path("endDate").asText("-") + "\n"
                + "· " + t(ko, "purpose", "목적") + ": " + c.path("purpose").asText("-") + "\n";
    }

    private List<TripPlanAgentResponse.PendingChoice> searchPlans(
            ObjectNode state, long corpUserId, String start, String end, String token,
            List<String> subAgents, StringBuilder reply, boolean ko) {
        JsonNode list = bizplayGatewayService.getPlanList(corpUserId, start, end, token);
        subAgents.add("PLAN_SEARCH_TOOL");
        // The scoped list honours startDate/endDate, so these rows are already the user's period;
        // the overlap filter below is what keeps that promise if the provider ever changes. It
        // also drops everything that is not APPROVED — BizPlay refuses a settlement anchored on
        // a plan no approver has signed, so an unapproved plan must never reach the picker.
        ArrayNode candidates = planCandidates(list, start, end);
        boolean inPeriod = !candidates.isEmpty();
        if (!inPeriod) {
            candidates = planCandidates(list, null, null);
        }
        state.set("planCandidates", candidates);
        state.put("stage", "AWAIT_PLAN_PICK");
        if (candidates.isEmpty()) {
            reply.append(t(ko,
                    "No approved trip plan between " + start + " and " + end
                            + ". Try a different period — or ask me which requests are still waiting "
                            + "for approval, since those can't be settled yet. ",
                    start + " ~ " + end + " 기간에는 승인된 출장 계획이 없습니다. 다른 기간을 말씀해 "
                            + "주시거나, 승인 대기 중인 신청 목록을 요청해 주세요 — 승인 전에는 "
                            + "정산할 수 없습니다. "));
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
        return planCandidates(list, start, end, 8);   // chip/table display: the 8 most recent
    }

    /**
     * @param limit how many rows to keep. The display paths want a handful; searching BY NAME
     *              must scan the whole window, or a trip just outside the first rows looks missing.
     */
    private ArrayNode planCandidates(JsonNode list, String start, String end, int limit) {
        return planCandidates(list, start, end, limit, STATUS_APPROVED);
    }

    /**
     * @param status keep only plans in this approval state, or null for every state.
     *               A settlement can only ever ride an {@value #STATUS_APPROVED} plan — a
     *               {@value #STATUS_DRAFTED} one has not been approved yet, so BizPlay refuses it
     *               as the anchor. Every settlement path therefore filters on APPROVED; the
     *               pending-plans tool asks for DRAFTED instead.
     */
    private ArrayNode planCandidates(JsonNode list, String start, String end, int limit, String status) {
        ArrayNode candidates = objectMapper.createArrayNode();
        java.util.List<ObjectNode> picked = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (list == null || !list.isArray()) {
            return candidates;
        }
        for (JsonNode p : list) {
            String from = day(p.path("bstrStartDate").asText(""));
            String to = day(p.path("bstrEndDate").asText(""));
            String state = p.path("approvalStatusType").asText("");
            if (status != null && !status.equalsIgnoreCase(state)) {
                continue;
            }
            if (start != null) {
                // Overlap test on ISO days (lexical order == chronological order).
                if (from.isEmpty() || to.isEmpty() || from.compareTo(end) > 0 || to.compareTo(start) < 0) {
                    continue;
                }
            }
            // The unscoped list repeats a plan once per approval line, and copies share a title —
            // so dedupe on the plan identity when there is one, on the display key otherwise.
            String key = p.hasNonNull("approvalId")
                    ? "id:" + p.path("approvalId").asLong()
                    : p.path("title").asText() + "|" + from + "|" + to;
            if (!seen.add(key)) {
                continue;
            }
            ObjectNode c = objectMapper.createObjectNode();
            c.put("approvalId", p.path("approvalId").asLong());
            c.put("docNo", p.path("docNo").asText(""));
            c.put("title", p.path("title").asText(""));
            c.put("startDate", from);
            c.put("endDate", to);
            c.put("purpose", p.path("bstrPurpose").asText(""));
            c.put("drafter", p.path("draftUserName").asText(""));
            c.put("registrar", p.path("regUserName").asText(""));
            // How many expense reports this plan already has. The provider is explicit: do NOT
            // hide plans with usageCnt > 0 — companions and additional reports are legitimate —
            // just show the count (260810 answer, 1-5/1-6). Only the seah path populates it.
            c.put("usageCnt", p.path("usageCnt").asInt(0));
            c.put("status", state);
            // When it was filed. Several plans can share a title, a destination and even a trip
            // date, so this is what tells "the one I just created" from the rest.
            c.put("created", minute(p.path("draftDateTime").asText("")));
            picked.add(c);
        }
        // Newest first: the plan someone is looking for right after filing it is the last one they
        // filed. Applied before the cap so the limit keeps the newest, not the provider's order.
        picked.sort((a, b) -> b.path("created").asText("").compareTo(a.path("created").asText("")));
        for (ObjectNode c : picked) {
            if (candidates.size() >= limit) {
                break;
            }
            candidates.add(c);
        }
        return candidates;
    }

    /** "2026-08-21T15:26:35.123" -> "2026-08-21 15:26" (blank stays blank). */
    private String minute(String value) {
        if (value == null || value.length() < 16) {
            return value == null ? "" : value;
        }
        return value.substring(0, 16).replace('T', ' ');
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
            // The form lists no expense types, so there is no kind a receipt could be filed under —
            // and one registered without a kind is created NOT ISSUED and can never be attached.
            // Name the paper so an admin can fix it rather than leaving a dead end.
            state.put("stage", "AWAIT_PLAN_PICK");
            reply.append(head).append(t(ko,
                    "This trip's form lists no expense types, so I can't register or attach evidence "
                            + "against it — the paper needs its 경비 항목 set up in BizPlay first. "
                            + "Pick another trip if you have one. ",
                    "이 출장의 양식에 경비 항목이 지정되어 있지 않아 증빙을 등록하거나 첨부할 수 없어요 — "
                            + "BizPlay에서 해당 양식에 경비 항목을 먼저 설정해 주세요. "
                            + "다른 출장이 있다면 선택해 주세요. "));
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
    /**
     * Is a receipt's approvalDate inside [start, end]? Dates arrive as "YYYY-MM-DD" (sometimes with
     * a time suffix), so a lexicographic compare on the first 10 chars is exact. An unparsable or
     * missing date is kept — never hide evidence because of a format surprise.
     */
    private boolean withinWindow(String date, String start, String end) {
        if (date == null || date.length() < 10 || start == null || start.isEmpty()
                || end == null || end.isEmpty()) {
            return true;
        }
        String d = date.substring(0, 10);
        return d.compareTo(start) >= 0 && d.compareTo(end) <= 0;
    }

    // ---------------------------------------------------------------------------------------
    // Conversational request building: describe it in words -> slots -> ask what's missing ->
    // PREVIEW the request body -> the user confirms -> the external call runs. The form stays
    // available; this is the second way in. One Slot spec per external request kind.
    // ---------------------------------------------------------------------------------------

    /**
     * One turn of the conversational receipt build. Returns null when this message isn't about
     * building an expense, so the caller's stage machine handles it as usual.
     */
    private BizplayPlanAgentResponse conversationalExpenseTurn(
            ConversationalAgentSession session, ObjectNode state, ArrayNode documents,
            String message, boolean machineToken, String corpNo, String bizplayToken, boolean ko) {
        ObjectNode pending = pendingExpense(state);
        boolean building = pending.size() > 0;

        if (message.matches("(?i)\\s*expense-cancel.*")) {
            state.remove("pendingExpense");
            String reply = t(ko, "Dropped that receipt — nothing was registered.",
                    "해당 영수증 입력을 취소했어요 — 등록된 내용은 없습니다.");
            return simpleTurn(session, state, message, reply, "EXPENSE_CANCELLED", null);
        }
        if (message.matches("(?i)\\s*expense-confirm.*")) {
            if (!building) {
                return null;
            }
            List<Slot> slots = expenseSlots(state);
            if (!missingExpenseSlots(pending, slots).isEmpty()) {
                return null;   // not actually complete — fall through and keep asking
            }
            ObjectNode fields = expenseFieldsFromPending(pending);
            ObjectNode detail = expenseDetailFromPending(pending, state);
            enrichTransportDetail(detail, pending.path("seatGrade").asText(""), bizplayToken);
            state.remove("pendingExpense");
            appendTurn(session, "user", message);
            saveState(session, state);
            sessionRepo.save(session);
            // Same call the form's "Register receipt" makes — one etc-card POST, then the draft.
            return addManualExpense(session.getId().toString(), corpNo, fields, detail,
                    null, null, bizplayToken);
        }
        // A disambiguated stop: write the catalog's own label into the expense being built, so the
        // next pass resolves it exactly instead of ambiguously.
        Matcher stop = STOP_PICK.matcher(message);
        if (stop.matches() && building) {
            String field = "from".equalsIgnoreCase(stop.group(1)) ? "depart" : "arrival";
            String label = nodeName(vehicleNodes(pending.path("vehicleType").asText(""), bizplayToken),
                    stop.group(2), "");
            if (!label.isBlank()) {
                pending.put(field, label);
            }
            List<Slot> after = expenseSlots(state);
            List<Slot> stillMissing = missingExpenseSlots(pending, after);
            if (!stillMissing.isEmpty()) {
                Slot next = stillMissing.get(0);
                return simpleTurn(session, state, message, formFollowUpAgentService.composeFollowUp(
                        t(ko, "receipt", "영수증"), List.of(t(ko, next.en(), next.ko())), ko),
                        "EXPENSE_SLOT_PENDING", expenseAbandonChips(ko));
            }
            BizplayPlanAgentResponse ask = askAmbiguousStop(session, state, pending, message, bizplayToken, ko);
            return ask != null ? ask
                    : simpleTurn(session, state, message, expensePreview(pending, after, ko),
                            "EXPENSE_PREVIEW", expenseConfirmChips(ko));
        }
        if (machineToken || isDraftQuestion(message)) {
            return null;   // chips and questions belong to the stage machine / draft Q&A
        }

        // The expense type gates everything below: it decides which detail fields apply and,
        // crucially, a receipt POSTed without it comes back NOT ISSUED. When the user describes an
        // expense before anyone has asked, work the type out from the description; only ask when
        // that fails. Refusing at the end - after they typed the whole receipt - is a dead end.
        if (!slots(state).hasNonNull("evidenceTranKindId")) {
            BizplayPlanAgentResponse kindTurn =
                    ensureExpenseKind(session, state, message, bizplayToken, ko);
            if (kindTurn != null) {
                return kindTurn;
            }
        } else {
            // The kind is already set — from a chip, or from the previous expense — but the user may
            // be describing a DIFFERENT one now ("숙박비로 Osaka Bay Hotel…" while 교통비 was picked).
            // Checked on EVERY expense turn, not just mid-build: the kind is named in the opening
            // sentence, before anything is pending. Without it the lodging expense inherited the
            // transport slots and the flow asked for a vehicle that was never going to come.
            switchExpenseKindIfNamed(state, pending, message, bizplayToken, ko);
        }

        List<Slot> slots = expenseSlots(state);
        // What the previous turn asked for: the ask is always the FIRST still-missing slot, so
        // recomputing it here - before this message is merged - names the question on screen.
        List<Slot> asked = building ? missingExpenseSlots(pending, slots) : List.of();
        Slot answering = asked.isEmpty() ? null : asked.get(0);
        int filled = fillExpenseSlots(pending, message, slots, ko);
        filled += backfillVehicleType(pending, message);
        filled += backfillIsoDates(pending, message, slots);
        filled -= dropInventedToday(pending, message);
        filled += fillAskedSlot(pending, message, answering, ko);
        filled += defaultLodgingPaymentDate(pending);
        if (!building && filled == 0) {
            state.remove("pendingExpense");
            return null;   // nothing expense-like in this message — not our turn
        }
        if (!building && filled == 1 && pending.size() == 1 && pending.has("approvalDate")) {
            // A bare date is a period answer far more often than a new expense.
            state.remove("pendingExpense");
            return null;
        }
        // Transport: the carrier the user named IS the merchant ("by KTX" -> mestName KTX).
        // Asking "what's the merchant?" right after they said it reads as not listening.
        if (pending.path("mestName").asText("").isBlank()
                && !pending.path("vehicleType").asText("").isBlank()) {
            pending.put("mestName", pending.path("vehicleType").asText());
        }
        List<Slot> missing = missingExpenseSlots(pending, slots);
        if (!missing.isEmpty()) {
            // ONE question at a time, composed by the follow-up sub-agent (never a template).
            Slot next = missing.get(0);
            String ask = formFollowUpAgentService.composeFollowUp(
                    t(ko, "receipt", "영수증"), List.of(t(ko, next.en(), next.ko())), ko);
            // Always offer the exit. A question the user cannot answer - the wrong expense kind, or
            // a receipt they have changed their mind about - otherwise repeats forever, and typing
            // "done" or "skip" at it does nothing because neither is a slot value.
            return simpleTurn(session, state, message, ask, "EXPENSE_SLOT_PENDING", expenseAbandonChips(ko));
        }
        // A stop that matches several places in the catalog is not something to guess at: the
        // traveller knows which one they used, and a wrong node is a wrong receipt.
        BizplayPlanAgentResponse ambiguous =
                askAmbiguousStop(session, state, pending, message, bizplayToken, ko);
        if (ambiguous != null) {
            return ambiguous;
        }
        return simpleTurn(session, state, message, expensePreview(pending, slots, ko),
                "EXPENSE_PREVIEW", expenseConfirmChips(ko));
    }

    /**
     * When a departure or arrival name matches more than one stop in the vehicle's node catalog,
     * return a turn that asks which one; null when both ends are unambiguous (or resolve exactly,
     * or match the terminal master, or match nothing at all - none of which is a choice).
     */
    private BizplayPlanAgentResponse askAmbiguousStop(ConversationalAgentSession session,
                                                      ObjectNode state, ObjectNode pending,
                                                      String message, String token, boolean ko) {
        String vehicle = pending.path("vehicleType").asText("");
        if (vehicle.isBlank() || LOCATOR_FREE_VEHICLES.contains(vehicle)) {
            return null;   // no stop catalogue to be ambiguous about
        }
        JsonNode nodes = vehicleNodes(vehicle, token);
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            return null;
        }
        JsonNode terminals = null;
        try {
            terminals = bizplayGatewayService.getEtcCardTerminals(token);
        } catch (Exception e) {
            log.warn("Terminal master unavailable while checking stops: {}", e.getMessage());
        }
        for (String[] end : new String[][]{{"depart", "from"}, {"arrival", "to"}}) {
            String name = pending.path(end[0]).asText("");
            if (name.isBlank() || terminalId(terminals, vehicle, name) != null) {
                continue;   // a hub the terminal master already pins down
            }
            if (nodeId(nodes, name) != null) {
                continue;   // resolves to exactly one stop
            }
            ArrayNode options = nodeCandidates(nodes, name);
            if (options.size() < 2) {
                continue;   // nothing matched: the plain name is kept, no question worth asking
            }
            List<TripPlanAgentResponse.Option> chips = new ArrayList<>();
            for (JsonNode o : options) {
                chips.add(TripPlanAgentResponse.Option.builder()
                        .label(o.path("nodeName").asText(""))
                        .sendText("stop:" + end[1] + ":" + o.path("nodeId").asText(""))
                        .build());
            }
            String ask = "depart".equals(end[0])
                    ? t(ko, "There are several stops called \"" + name + "\" - which one did you leave from?",
                            "\"" + name + "\" 정류장이 여러 곳이에요 - 어디에서 출발하셨나요?")
                    : t(ko, "There are several stops called \"" + name + "\" - which one did you arrive at?",
                            "\"" + name + "\" 정류장이 여러 곳이에요 - 어디에 도착하셨나요?");
            return simpleTurn(session, state, message, ask, "STOP_PICK_PENDING",
                    List.of(TripPlanAgentResponse.PendingChoice.builder()
                            .kind("STOP").name(t(ko, "stop", "정류장")).options(chips).build()));
        }
        return null;
    }

    /** The node catalog for a vehicle, or null - never throws, the caller treats null as "no help". */
    private JsonNode vehicleNodes(String vehicleType, String token) {
        if (vehicleType == null || vehicleType.isBlank()) {
            return null;
        }
        try {
            return bizplayGatewayService.getVehicleNodes(vehicleType, token);
        } catch (Exception e) {
            log.warn("Node list unavailable for {}: {}", vehicleType, e.getMessage());
            return null;
        }
    }

    /**
     * Every stop whose catalog name contains what the user said, capped for a chip row. This is the
     * "wider" match of {@link #nodeId} - the one that goes ambiguous when a name like 금호리조트
     * covers both 봉개 and 교래.
     */
    private ArrayNode nodeCandidates(JsonNode nodes, String name) {
        ArrayNode out = objectMapper.createArrayNode();
        if (nodes == null || !nodes.isArray() || name == null || name.isBlank()) {
            return out;
        }
        String wanted = name.trim().toLowerCase(java.util.Locale.ROOT);
        for (JsonNode n : nodes) {
            String candidate = n.path("nodeName").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            if (!candidate.isEmpty() && candidate.contains(wanted)) {
                out.add(n.deepCopy());
                if (out.size() >= CHIP_LIMIT) {
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Settle the expense type before an expense is built. Returns null when the type is known (the
     * caller carries on); otherwise a turn that either confirms the inferred type or asks for it.
     * <p>Inference runs the SAME slot-filler used for the rest of the receipt, given the corp's own
     * kind names - so "Marriott, checked in..." lands on 숙박비 without any hard-coded vocabulary.
     */
    private BizplayPlanAgentResponse ensureExpenseKind(ConversationalAgentSession session,
                                                       ObjectNode state, String message,
                                                       String token, boolean ko) {
        ArrayNode kinds = (ArrayNode) slots(state).withArray("planTranKinds");
        if (kinds.isEmpty()) {
            return null;   // nothing to choose from - the caller's own guard reports it
        }
        List<String> names = new ArrayList<>();
        for (JsonNode k : kinds) {
            String n = k.path("name").asText("");
            if (!n.isBlank()) {
                names.add(n);
            }
        }
        JsonNode picked = objectMapper.createObjectNode();
        try {
            picked = slotFillerAgentService.extract(message, java.util.Map.of(
                    "expenseKind", "which expense category the described expense belongs to \u2014 "
                            + "EXACTLY one of these names, copied verbatim: " + String.join(", ", names)
                            + ". A hotel or a stay is lodging, a flight/train/taxi/bus is transport, "
                            + "a meal is food. Omit the field unless the message clearly says what "
                            + "the money was spent on"), ko);
        } catch (Exception e) {
            log.warn("Expense-kind inference failed ({}); asking instead.", e.getMessage());
        }
        String guess = picked.path("expenseKind").asText("").trim();
        if (!guess.isEmpty()) {
            for (JsonNode k : kinds) {
                if (guess.equalsIgnoreCase(k.path("name").asText(""))) {
                    selectTranKind(state, k.path("id").asLong());
                    log.info("Expense kind inferred from the description: {} ({})",
                            k.path("name").asText(""), k.path("id").asLong());
                    return null;   // known now - build the expense in this same turn
                }
            }
        }
        // Could not tell: ask, and keep what they already typed so it is not retyped.
        String ask = t(ko,
                "Which expense type is this? ", "어떤 경비 항목인가요? ");
        return simpleTurn(session, state, message, ask, "TRANKIND_PENDING", tranKindChips(state, ko));
    }

    /**
     * Naming another expense type mid-build switches to it, and drops the answers that belonged to
     * the old one — a hotel keeps no departure station, a taxi keeps no check-out date. Silent when
     * the message names nothing recognisable, so ordinary answers never disturb the chosen kind.
     */
    private void switchExpenseKindIfNamed(ObjectNode state, ObjectNode pending, String message,
                                          String token, boolean ko) {
        ArrayNode kinds = (ArrayNode) slots(state).withArray("planTranKinds");
        if (kinds.isEmpty() || message == null || message.isBlank()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode k : kinds) {
            String n = k.path("name").asText("");
            if (!n.isBlank()) {
                names.add(n);
            }
        }
        String guess;
        try {
            guess = slotFillerAgentService.extract(message, java.util.Map.of(
                    "expenseKind", "the expense category the user names in THIS message — EXACTLY one "
                            + "of: " + String.join(", ", names) + ". Omit the field unless they name "
                            + "one explicitly"), ko).path("expenseKind").asText("").trim();
        } catch (Exception e) {
            return;
        }
        if (guess.isEmpty()) {
            return;
        }
        for (JsonNode k : kinds) {
            if (guess.equalsIgnoreCase(k.path("name").asText(""))
                    && k.path("id").asLong() != slots(state).path("evidenceTranKindId").asLong()) {
                selectTranKind(state, k.path("id").asLong());
                for (String stale : new String[]{"vehicleType", "depart", "arrival", "seatGrade",
                        "usedStartDate", "usedEndDate"}) {
                    pending.remove(stale);
                }
                log.info("Expense kind switched mid-build to {} — kind-specific answers cleared.",
                        k.path("name").asText(""));
                return;
            }
        }
    }

    /** The way out of a half-built receipt, on every question that could otherwise repeat forever. */
    private List<TripPlanAgentResponse.PendingChoice> expenseAbandonChips(boolean ko) {
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("EXPENSE_ABANDON")
                .name(t(ko, "or", "또는"))
                .options(List.of(TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Cancel this receipt", "이 영수증 취소"))
                        .sendText("expense-cancel").build()))
                .build());
    }

    /** Persist the turn and answer with reply + chips — the shape every branch above returns. */
    private BizplayPlanAgentResponse simpleTurn(ConversationalAgentSession session, ObjectNode state,
                                                String message, String reply, String intent,
                                                List<TripPlanAgentResponse.PendingChoice> chips) {
        appendTurn(session, "user", message);
        appendTurn(session, "assistant", reply);
        saveState(session, state);
        ConversationalAgentSession saved = sessionRepo.save(session);
        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent(intent)
                .subAgents(List.of("SETTLEMENT_AGENT", "SLOT_FILLER_AGENT"))
                .reply(reply)
                .pendingChoices(chips)
                .draftJson(saved.getDraftJson())
                .build();
    }

    /** One field of an external request body: how to ask for it, and whether it blocks the call. */
    private record Slot(String key, String en, String ko, String meaning, boolean required) { }

    /** ⑧ etc-card receipt — the base body every manual expense needs. */
    private static final List<Slot> EXPENSE_SLOTS = List.of(
            new Slot("mestName", "merchant", "가맹점",
                    "merchant / store / carrier name the money was paid to (e.g. \"KTX\", \"서울역 카페\")", true),
            new Slot("approvalDate", "receipt date", "일자",
                    "date of the expense, ISO yyyy-MM-dd", true),
            new Slot("approvalAmount", "amount", "금액",
                    "total amount paid, digits only, no currency symbol or separators", true),
            // RETIRED (kept for reference, do not delete): the tax slot. The etc-receipt body
            // carries supplyAmount/vatAmount as null, so neither is collected any more.
            new Slot("approvalTime", "time", "시각",
                    "time of the expense as HH:mm:ss, only if the user said one", false),
            new Slot("currencyCode", "currency", "통화",
                    "ISO currency code, only if the user named one (default KRW)", false));

    /** Transport (교통비) receipts carry a detail block on top of the base. */
    private static final List<Slot> EXPENSE_TRANSPORT_SLOTS = List.of(
            new Slot("vehicleType", "transport", "교통수단",
                    "one of AIR, KTX, SRT, TRAIN, BUS, CBUS, TAXI, RENTAL, CORP_CAR, AIRPORT_LIMOUSINE, OTHER"
                            + " — KTX/SRT stay as themselves, 기차/열차 -> TRAIN, 고속버스 -> BUS,"
                            + " 시외버스 -> CBUS, 택시 -> TAXI, 렌터카 -> RENTAL, 항공/비행기 -> AIR,"
                            + " 법인차량/회사차량 -> CORP_CAR, 공항리무진 -> AIRPORT_LIMOUSINE,"
                            + " 기타 교통수단 -> OTHER."
                            + " The vehicle word is usually written in the message itself: if one of"
                            + " those words appears, ALWAYS emit the matching value. Do this even when"
                            + " the place names are unfamiliar or carry no 역/공항/터미널 suffix"
                            + " — small stations like 가남 or 감공장호원 are still stations, and they never"
                            + " change what the vehicle was", true),
            new Slot("depart", "departure place", "출발지",
                    "where the trip legs started, copied verbatim from the user. A bare place name"
                            + " with no 역/공항 suffix (가남, 광주) is a perfectly valid answer"
                            + " — never omit it because the name looks unfamiliar", true),
            new Slot("arrival", "arrival place", "도착지",
                    "where the trip legs ended, copied verbatim from the user. A bare place name"
                            + " with no 역/공항 suffix is a perfectly valid answer", true),
            // Optional, and normalised to OUR tokens rather than the provider's — their wire values
            // differ per vehicle (economyClass / ECONOMY_KTX / standard) and only the 일반 grade is
            // confirmed, so the mapping to a real value happens in code, not here.
            new Slot("seatGrade", "seat grade", "좌석등급",
                    "the seat grade IF the user named one — STANDARD for 일반/general/economy,"
                            + " SUPERIOR for 우등, PREMIUM for 프리미엄. Omit the field entirely"
                            + " when no grade is mentioned", false));

    /** Lodging (숙박비) receipts carry the stay dates — distinct from the payment date. */
    private static final List<Slot> EXPENSE_ROOM_SLOTS = List.of(
            new Slot("usedStartDate", "check-in", "체크인",
                    "check-in date of the stay, ISO yyyy-MM-dd", true),
            new Slot("usedEndDate", "check-out", "체크아웃",
                    "check-out date of the stay, ISO yyyy-MM-dd", true));

    /** The slots for the expense being built — transport adds its own. */
    private List<Slot> expenseSlots(ObjectNode state) {
        List<Slot> all = new ArrayList<>(EXPENSE_SLOTS);
        List<String> detail = detailFieldsForType(slots(state).path("evidenceTranKindType").asText(null));
        if (detail.contains("vehicleType")) {
            all.addAll(EXPENSE_TRANSPORT_SLOTS);
        } else if (detail.contains("roomType")) {
            all.addAll(EXPENSE_ROOM_SLOTS);
        }
        return all;
    }

    private ObjectNode pendingExpense(ObjectNode state) {
        JsonNode existing = state.get("pendingExpense");
        if (existing != null && existing.isObject()) {
            return (ObjectNode) existing;
        }
        ObjectNode fresh = objectMapper.createObjectNode();
        state.set("pendingExpense", fresh);
        return fresh;
    }

    /**
     * Merge whatever this message states into the pending expense. Returns the number of slots the
     * message actually contributed — 0 means "this wasn't about an expense", which is how a new
     * conversational expense is recognised without any phrase list.
     */
    private int fillExpenseSlots(ObjectNode pending, String message, List<Slot> slots, boolean ko) {
        java.util.LinkedHashMap<String, String> wanted = new java.util.LinkedHashMap<>();
        for (Slot s : slots) {
            wanted.put(s.key(), s.meaning());
        }
        JsonNode got = slotFillerAgentService.extract(message, wanted, ko);
        int filled = 0;
        for (Slot s : slots) {
            JsonNode v = got.path(s.key());
            if (v.isMissingNode() || v.isNull() || v.asText("").isBlank()) {
                continue;
            }
            // A later turn corrects an earlier one — always take the newest value.
            pending.set(s.key(), v.deepCopy());
            filled++;
        }
        return filled;
    }

    /** Required slots still empty, in ask order. */
    /**
     * The vehicle words, in the order they must be tested: the longer compound wins, or
     * 시외버스 would be read as 버스. These are the provider's own enum values, not
     * conversational phrasing - matching them is normalising data, not guessing intent.
     */
    private static final java.util.List<String[]> VEHICLE_WORDS = java.util.List.of(
            new String[]{"법인차량", "CORP_CAR"},
            new String[]{"법인 차량", "CORP_CAR"},
            new String[]{"회사차량", "CORP_CAR"},
            new String[]{"company car", "CORP_CAR"},
            new String[]{"corporate car", "CORP_CAR"},
            new String[]{"기타 교통수단", "OTHER"},
            new String[]{"기타교통", "OTHER"},
            new String[]{"공항리무진", "AIRPORT_LIMOUSINE"},
            new String[]{"리무진", "AIRPORT_LIMOUSINE"},
            new String[]{"limousine", "AIRPORT_LIMOUSINE"},
            new String[]{"시외버스", "CBUS"},
            new String[]{"고속버스", "BUS"},
            new String[]{"ktx", "KTX"},
            new String[]{"srt", "SRT"},
            new String[]{"렌터카", "RENTAL"},
            new String[]{"rental", "RENTAL"},
            new String[]{"택시", "TAXI"},
            new String[]{"taxi", "TAXI"},
            new String[]{"기차", "TRAIN"},
            new String[]{"열차", "TRAIN"},
            new String[]{"train", "TRAIN"},
            new String[]{"비행기", "AIR"},
            new String[]{"항공", "AIR"},
            new String[]{"flight", "AIR"},
            new String[]{"버스", "BUS"});

    /**
     * Backstop for the vehicle when the extractor drops it. A thousand-separator in the amount is
     * enough to do that - "KTX 43,500원 서울역에서 대전역까지" came back with no vehicle
     * three times out of three, while the same sentence written 43500원 parsed fine. The word is
     * sitting in the message either way, so re-asking for it reads as not listening.
     */
    private int backfillVehicleType(ObjectNode pending, String message) {
        if (message == null || message.isBlank() || !pending.path("vehicleType").asText("").isBlank()) {
            return 0;
        }
        String haystack = message.toLowerCase(java.util.Locale.ROOT);
        for (String[] pair : VEHICLE_WORDS) {
            if (haystack.contains(pair[0])) {
                pending.put("vehicleType", pair[1]);
                log.info("Vehicle {} recovered from the message text after the extractor missed it.", pair[1]);
                return 1;
            }
        }
        return 0;
    }

    /**
     * Words and shapes that mean "this message mentions a date". Deliberately generous - the only
     * job is to tell "택시비 18000원" (no date at all) apart from anything that does name one.
     */
    private static final java.util.regex.Pattern DATE_MENTIONED = java.util.regex.Pattern.compile(
            "(?iu)(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[./-]\\d{1,2}|\\d{1,2}\\s*\uc6d4|\\d{1,2}\\s*\uc77c"
            + "|\uc624\ub298|\uc5b4\uc81c|\ub0b4\uc77c|\ubaa8\ub808|\uadf8\uc81c|\uc9c0\ub09c|\uc774\ubc88|\uc694\uc77c"
            + "|today|yesterday|tomorrow|last|this|next"
            + "|mon|tue|wed|thu|fri|sat|sun"
            + "|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)");

    /**
     * The extractor is told what today's date is, so that when a message names no date at all it
     * tends to answer with today rather than nothing. "택시비 18000원" came back stamped
     * 2026-08-24 on a trip that ran 08-27~28 - evidence dated outside the trip it belongs to, filed
     * without anyone being asked. Today is only dropped when the message mentions no date
     * whatsoever, so a real "오늘 택시비" still means today.
     *
     * The payment date legitimately falls outside the trip window (the provider's own sample pays
     * on 08-17 for a stay on 08-14~15), so this asks rather than clamping to the trip period.
     */
    private int dropInventedToday(ObjectNode pending, String message) {
        String date = pending.path("approvalDate").asText("");
        if (date.isBlank() || !date.equals(LocalDate.now().toString())) {
            return 0;
        }
        if (message != null && DATE_MENTIONED.matcher(message).find()) {
            return 0;   // they said something date-like - today may well be what they meant
        }
        pending.remove("approvalDate");
        log.info("Dropped an invented approvalDate of today - the message named no date, so the "
                + "flow will ask instead of stamping the receipt with the wrong day.");
        return 1;
    }

    /**
     * Second, narrow pass for the one slot the user was actually asked about. The broad pass offers
     * the model every slot at once, so an answer to "what was the date of the receipt?" gets mapped
     * to whichever date field fits best - on a lodging receipt that is check-in/check-out, already
     * filled, leaving approvalDate empty and the same question on screen again. Re-sending the
     * whole sentence does not help, because it maps the same way every time.
     *
     * Only runs when the broad pass left the asked slot empty, so a message that answered properly
     * is never re-interpreted.
     */
    private int fillAskedSlot(ObjectNode pending, String message, Slot answering, boolean ko) {
        if (answering == null || message == null || message.isBlank()) {
            return 0;
        }
        JsonNode have = pending.path(answering.key());
        boolean stillEmpty = have.isMissingNode() || have.isNull() || have.asText("").isBlank()
                || ("approvalAmount".equals(answering.key()) && have.asDouble(0) <= 0);
        if (!stillEmpty) {
            return 0;
        }
        try {
            JsonNode got = slotFillerAgentService.extract(message,
                    java.util.Map.of(answering.key(), answering.meaning()), ko);
            JsonNode v = got.path(answering.key());
            if (!v.isMissingNode() && !v.isNull() && !v.asText("").isBlank()) {
                pending.set(answering.key(), v.deepCopy());
                log.info("Slot {} filled by the focused pass - the broad extraction had mapped the "
                        + "answer elsewhere.", answering.key());
                return 1;
            }
        } catch (Exception e) {
            log.warn("Focused slot extraction failed for {}: {}", answering.key(), e.getMessage());
        }
        return 0;
    }

    /**
     * A manually entered hotel receipt is paid for the stay it describes, so the check-in date is
     * the payment date unless the traveller says otherwise. Asking separately produced a question
     * with no good answer - "8월 27일부터 29일까지" is the stay, and every rephrasing of it
     * mapped back to the stay. (Card receipts are different: their payment date comes from the
     * transaction, which really can sit outside the stay - the provider's own sample pays 08-17
     * for 08-14~15. That path never reaches here.)
     */
    private int defaultLodgingPaymentDate(ObjectNode pending) {
        String stay = pending.path("usedStartDate").asText("");
        if (stay.isBlank() || !pending.path("approvalDate").asText("").isBlank()) {
            return 0;
        }
        pending.put("approvalDate", stay);
        log.info("Lodging payment date defaulted to the check-in date {}.", stay);
        return 1;
    }

    /** The date slots, in the order they are asked - which is the order a bare answer fills them. */
    private static final List<String> DATE_SLOT_KEYS =
            List.of("approvalDate", "usedStartDate", "usedEndDate");

    /**
     * Backstop for a date the extractor dropped, in the same spirit as
     * {@link #backfillVehicleType}. Answering "영수증의 일자는 언제인가요?" with a bare
     * "2026-08-27" came back empty every time - with no words around it the model cannot tell
     * which of approvalDate / usedStartDate / usedEndDate is meant, so it emits none of them and
     * the same question is asked again, forever. "8월 27일" parses fine, so the loop only hit
     * users who typed the ISO form the slot itself asks for.
     *
     * Dates found are assigned to the still-empty date slots in ask order, so one date answers the
     * question on screen and "2026-08-27 2026-08-28" fills check-in and check-out together.
     */
    private int backfillIsoDates(ObjectNode pending, String message, List<Slot> slots) {
        if (message == null || message.isBlank()) {
            return 0;
        }
        Matcher m = ISO_DATE.matcher(message);   // the class's existing yyyy-MM-dd pattern
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        if (found.isEmpty()) {
            return 0;
        }
        List<String> open = new ArrayList<>();
        for (String key : DATE_SLOT_KEYS) {
            boolean inPlay = slots.stream().anyMatch(sl -> sl.key().equals(key));
            JsonNode v = pending.path(key);
            if (inPlay && (v.isMissingNode() || v.isNull() || v.asText("").isBlank())) {
                open.add(key);
            }
        }
        int filled = 0;
        for (int i = 0; i < open.size() && i < found.size(); i++) {
            pending.put(open.get(i), found.get(i));
            log.info("Date {} recovered from the message text into {} after the extractor missed it.",
                    found.get(i), open.get(i));
            filled++;
        }
        return filled;
    }

    private List<Slot> missingExpenseSlots(ObjectNode pending, List<Slot> slots) {
        List<Slot> missing = new ArrayList<>();
        for (Slot s : slots) {
            if (!s.required()) {
                continue;
            }
            JsonNode v = pending.path(s.key());
            boolean empty = v.isMissingNode() || v.isNull() || v.asText("").isBlank()
                    || ("approvalAmount".equals(s.key()) && v.asDouble(0) <= 0);
            if (empty) {
                missing.add(s);
            }
        }
        return missing;
    }

    /** The request body, laid out for the user to check before it is sent. */
    private String expensePreview(ObjectNode pending, List<Slot> slots, boolean ko) {
        StringBuilder sb = new StringBuilder(t(ko,
                "Here's the receipt I'll register — check it and confirm:\n",
                "등록할 영수증이에요 — 확인하고 승인해 주세요:\n"));
        for (Slot s : slots) {
            String v = pending.path(s.key()).asText("");
            if (v.isBlank()) {
                continue;
            }
            if ("approvalAmount".equals(s.key())) {
                // Show the currency the user actually named. Stamping ₩ on a USD amount made a
                // 45-dollar taxi read as 45 won in the very card meant for checking it.
                String cur = pending.path("currencyCode").asText("KRW");
                String amount = String.format("%,d", pending.path(s.key()).asLong());
                v = "KRW".equalsIgnoreCase(cur) || cur.isBlank() ? "₩" + amount : amount + " " + cur;
            }
            sb.append("· ").append(t(ko, s.en(), s.ko())).append(": ").append(v).append('\n');
        }
        return sb.toString();
    }

    private List<TripPlanAgentResponse.PendingChoice> expenseConfirmChips(boolean ko) {
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("EXPENSE_CONFIRM").name(t(ko, "register this receipt?", "이 영수증을 등록할까요?"))
                .options(List.of(
                        TripPlanAgentResponse.Option.builder()
                                .label(t(ko, "Register", "등록")).sendText("expense-confirm").build(),
                        TripPlanAgentResponse.Option.builder()
                                .label(t(ko, "Cancel", "취소")).sendText("expense-cancel").build()))
                .build());
    }

    /** Pending slots -> the etc-card base body the form would have posted. */
    private ObjectNode expenseFieldsFromPending(ObjectNode pending) {
        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("mestName", pending.path("mestName").asText(""));
        fields.put("approvalDate", pending.path("approvalDate").asText(""));
        String time = pending.path("approvalTime").asText("");
        fields.put("approvalTime", time.isBlank() ? "12:00:00" : (time.length() == 5 ? time + ":00" : time));
        String currency = pending.path("currencyCode").asText("");
        fields.put("currencyCode", currency.isBlank() ? "KRW" : currency);
        fields.put("overseasUsed", pending.path("overseasUsed").asBoolean(false));
        fields.put("approvalAmount", pending.path("approvalAmount").asLong(0));
        // The captured etc-receipt request carries both as null - neither is asked for.
        fields.putNull("supplyAmount");
        fields.putNull("vatAmount");
        return fields;
    }

    /** Pending slots -> the transport detail block, mirroring the form's readTransportDetail(). */
    /**
     * Pending slots -> the etc-card detail block. Per the provider's 260810 answer (3-3.2), EVERY
     * purpose-specific field is sent explicitly, with null for the ones this purpose doesn't use —
     * omitting them (or sending a field the DTO doesn't know) is a 400, not a silent ignore.
     */
    private ObjectNode expenseDetailFromPending(ObjectNode pending, ObjectNode state) {
        List<String> detailKeys = detailFieldsForType(slots(state).path("evidenceTranKindType").asText(null));
        boolean transport = detailKeys.contains("vehicleType");
        boolean lodging = detailKeys.contains("roomType");
        if (!transport && !lodging) {
            return null;
        }
        ObjectNode d = objectMapper.createObjectNode();
        d.put("etcReceiptType", "RECEIPT");
        // Stay dates for lodging; for transport the travel day doubles as the usage date.
        String usedStart = pending.path(lodging ? "usedStartDate" : "approvalDate").asText("");
        String usedEnd = pending.path(lodging ? "usedEndDate" : "approvalDate").asText("");
        putOrNull(d, "usedStartDate", usedStart);
        putOrNull(d, "usedEndDate", usedEnd);
        // Transport block
        putOrNull(d, "vehicleType", transport ? pending.path("vehicleType").asText("") : "");
        d.putNull("routeType");
        d.putNull("seatClass");
        d.putNull("departTerminalId");
        d.putNull("arrivalTerminalId");
        d.putNull("departNodeId");
        d.putNull("arrivalNodeId");
        putOrNull(d, "depart", transport ? pending.path("depart").asText("") : "");
        putOrNull(d, "arrival", transport ? pending.path("arrival").asText("") : "");
        // Lodging block
        d.putNull("starRating");
        d.putNull("roomType");
        d.putNull("partnerHotel");
        // Meal block + common
        d.putNull("personCount");
        d.putNull("foodDivisionType");
        d.putNull("cancelReason");
        d.putNull("outPolicyReason");
        return d;
    }

    private void putOrNull(ObjectNode node, String key, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(key);
        } else {
            node.put(key, value);
        }
    }

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
        // The IMPORTED PLAN's period bounds the evidence: a trip on 2026-08-10 shows only
        // receipts dated 2026-08-10; a 2026-08-10~08-11 trip only those two days.
        String planStart = state.path("anchor").path("startDate").asText("");
        String planEnd = state.path("anchor").path("endDate").asText("");
        String tripStart = planStart.isEmpty() ? state.path("evidenceStart").asText("") : planStart;
        String tripEnd = planEnd.isEmpty() ? state.path("evidenceEnd").asText("") : planEnd;
        // RETIRED (kept for reference, do not delete): the window used to be widened -7/+30 days
        // around the trip to catch manually-registered receipts dated near it. The demo now shows
        // strictly in-trip evidence; the receipt browser (calendar) remains the tool for the rest.
        // String[] win = widenWindow(tripStart, tripEnd);
        // String start = win[0], end = win[1];
        String start = tripStart, end = tripEnd;
        // Record the receipt-endpoint params in the slot bag (data in hand for this + later turns).
        ObjectNode slots = slots(state);
        slots.put("evidenceStart", start);
        slots.put("evidenceEnd", end);
        ArrayNode ct = slots.putArray("cardTypes");
        cardTypes.forEach(ct::add);
        // Scope to the expense type the user picked: a receipt belongs to exactly one TranKind and
        // may only be listed, and only be attached, under that kind. WHICH kinds exist is decided
        // by the plan's own paper — two here (교통비/숙박비), none there, more elsewhere — so the
        // filter is always the picked id, never a fixed catalogue.
        //
        // No excludeTranKindIds: the provider's samples all carry excludeTranKindIds=11717, but
        // 11717 (기타증빙) is simply one kind on THEIR form and appears on none of these plans.
        // Measured against this corp's data it changes nothing (58 rows either way), so hardcoding
        // another form's id would be superstition, not filtering.
        Long pickedKind = slots.hasNonNull("evidenceTranKindId")
                ? slots.path("evidenceTranKindId").asLong() : null;
        JsonNode receipts = bizplayGatewayService.getUnattachedReceipts(
                corpUserId, start, end, cardTypes,
                pickedKind == null ? null : List.of(pickedKind), null, token);
        subAgents.add("RECEIPT_LOOKUP_TOOL");
        ArrayNode candidates = objectMapper.createArrayNode();
        if (receipts != null && receipts.isArray()) {
            for (JsonNode r : receipts) {
                if (r.path("approvalCanceled").asBoolean(false)) {
                    continue;   // canceled card approvals are not attachable evidence
                }
                // Enforce the trip window ourselves: the provider's list endpoints have been seen
                // ignoring their date params (same rows for every range), so filtering here is what
                // actually keeps out-of-trip receipts off the list.
                // Same reasoning as the query: judge a receipt by when it was USED, falling back to
                // the payment date for kinds that carry no usage dates (a taxi, a meal).
                String used = r.path("usedStartDate").asText("");
                if (used.isBlank()) {
                    used = r.path("approvalDate").asText("");
                }
                if (!withinWindow(used, start, end)) {
                    continue;
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
                                String token, StringBuilder reply, boolean ko) {
        if (documents.isEmpty()) {
            reply.append(t(ko, "No settlement draft in progress. ", "진행 중인 정산서가 없습니다. "));
            return;
        }
        ObjectNode doc = (ObjectNode) documents.get(0);
        ArrayNode candidates = (ArrayNode) state.withArray("receiptCandidates");
        boolean all = "all".equalsIgnoreCase(pick);
        long wantedId = all || !pick.matches("\\d+") ? -1 : Long.parseLong(pick);

        // What this turn attaches, collected first so their ISSUED children resolve in ONE
        // lookup (the endpoint takes a comma-joined id list).
        List<JsonNode> picked = new ArrayList<>();
        for (JsonNode c : candidates) {
            long receiptId = c.path("id").asLong();
            if ((!all && receiptId != wantedId) || alreadyAttached(doc, receiptId)) {
                continue;
            }
            picked.add(c);
        }
        java.util.Map<Long, JsonNode> issuedByReceipt = issuedChildren(picked, token);
        // Slip sources, fetched once and only if a receipt actually needs a slip built.
        JsonNode tranKinds = null;
        JsonNode budgetDept = null;

        List<String> added = new ArrayList<>();
        List<String> notIssued = new ArrayList<>();
        for (JsonNode c : picked) {
            long receiptId = c.path("id").asLong();
            JsonNode child = issuedByReceipt.get(receiptId);
            if (child == null) {
                // No ISSUED child — the save would come back 400 "발급된 영수증이 존재하지
                // 않습니다". Say it here, where the user can still do something about it.
                notIssued.add(c.path("mestName").asText(""));
                continue;
            }
            long issuedReceiptId = child.path("id").asLong();
            // The stream row carries the card facts; the issued child carries the settlement facts
            // (tranKind, split amounts, slip). Merge so both are available downstream.
            ObjectNode enriched = ((ObjectNode) c).deepCopy();
            enriched.set("issuedReceipt", child);
            doc.withArray("receiptIds").add(issuedReceiptId);
            doc.withArray("bstrReceipts").add(bstrReceipt(enriched, receiptId, issuedReceiptId));

            ObjectNode field = issuedField(enriched, receiptId, issuedReceiptId, doc);
            ObjectNode dto = (ObjectNode) field.withArray("issuedReceiptDtos").get(0);
            // These receipts carry no slip of their own, so copying one yields an empty shell:
            // no account subject, no cost centre, no branch office — and BizPlay answers the save
            // with the opaque PORTAL_ERROR_500_0005. Build the SAME slip the manual-expense path
            // builds, which is the shape the provider actually accepts.
            if (!dto.path("slip").hasNonNull("accountSubjectId")) {
                if (tranKinds == null) {
                    tranKinds = tranKindList(token);
                    budgetDept = firstBudgetDepartment(token);
                }
                dto.set("slip", minimalSlip(slipEntry(enriched, child), tranKinds, budgetDept));
            }
            // The working save (and the browser capture) carries the receipt's own kind on the dto.
            // As a whole number: firstNumber() is a double, and "11719.0" is not what an id looks
            // like on the wire.
            dto.put("tranKindId", (long) firstNumber(child.path("tranKindId"), enriched.path("tranKindId"), 0));
            dto.put("tranKindType", firstText(child.path("tranKindType"), enriched.path("tranKindType")));
            doc.withArray("issuedFields").add(field);
            added.add(c.path("mestName").asText("") + " ₩" + c.path("approvalAmount").asText(""));
        }
        recomputeTotals(doc);
        if (!notIssued.isEmpty()) {
            reply.append(t(ko,
                    "Not attached — no issued receipt yet, register it as an expense in BizPlay "
                            + "first: " + String.join(", ", notIssued) + ". ",
                    "첨부하지 못했습니다 — 아직 발급된 영수증이 아닙니다. BizPlay에서 먼저 경비로 "
                            + "등록해 주세요: " + String.join(", ", notIssued) + ". "));
        }
        if (added.isEmpty() && notIssued.isEmpty()) {
            reply.append(t(ko, "That receipt is already attached. ", "이미 첨부된 증빙입니다. "));
        } else if (!added.isEmpty()) {
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
        // MANDATORY: the provider's own sample omitted these two and that omission was the cause
        // of the opaque PORTAL_ERROR_500_0005 / COMM_ERROR on save (their 2026-08-19 answer:
        // "issuedFields[0].issuedReceiptDtos[0] 에 issuedItems 가 누락"). Empty arrays are what a
        // working browser save sends when the receipt area has no per-receipt input items.
        dto.putArray("issuedItems");
        dto.putArray("accountIssuedItems");
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

    /**
     * Seat class per vehicle, from the provider's own etc-card samples: a flight books
     * {@code economyClass}, a KTX {@code ECONOMY_KTX}. Vehicles with no sample stay null rather
     * than carry a guessed enum the server might reject.
     */
    private static final java.util.Map<String, String> SEAT_CLASS_BY_VEHICLE = java.util.Map.of(
            "AIR", "economyClass",
            "KTX", "ECONOMY_KTX",
            "SRT", "ECONOMY_KTX",
            // Both bus kinds offer \uc77c\ubc18 / \uc6b0\ub4f1 / \ud504\ub9ac\ubbf8\uc5c4, and \uc77c\ubc18 is "standard" in the provider's
            // own CBUS and BUS samples. \uc6b0\ub4f1 / \ud504\ub9ac\ubbf8\uc5c4 have no confirmed wire value, so they stay
            // null rather than guessed \u2014 the field is optional on the form.
            "CBUS", "standard",
            "BUS", "standard");

    /**
     * Bus seat grades: what the UI shows -> what the JSON carries. Buses are the only vehicles with
     * a real choice; a flight or a train has one fixed value, so this map is consulted for BUS and
     * CBUS only. Keys are the slot-filler's normalised tokens, not the user's words.
     */
    private static final java.util.Map<String, String> BUS_SEAT_GRADES = java.util.Map.of(
            // Lowercase, per the provider's own CBUS and express-bus captures — the field is not
            // validated server-side, so the captured spelling is the only reliable source.
            "STANDARD", "standard",     // 일반
            "SUPERIOR", "Superior",     // 우등
            "EXCELLENT", "Superior",    // the extractor's older token for 우등
            "PREMIUM", "Premium");      // 프리미엄

    /**
     * Vehicles that travel between places rather than between STOPS. A hired car, a company car,
     * an airport limousine and "other" are all driven wherever the traveller needs to go, so they
     * have no timetable, no seat and nothing to resolve against either locator catalogue. The
     * provider's RENTAL, CORP_CAR, AIRPORT_LIMOUSINE and OTHER samples agree exactly: routeType,
     * seatClass and all four locator ids null, with only the plain depart/arrival names carried.
     */
    private static final java.util.Set<String> LOCATOR_FREE_VEHICLES =
            java.util.Set.of("RENTAL", "CORP_CAR", "AIRPORT_LIMOUSINE", "OTHER");

    /** Bus kinds share the three-grade seat catalog; every other vehicle has a single fixed value. */
    private static boolean isBus(String vehicleType) {
        return "BUS".equals(vehicleType) || "CBUS".equals(vehicleType);
    }

    /**
     * Fill the transport fields the provider's samples carry beyond what the sentence gives us:
     * {@code routeType}, {@code seatClass}, and the depart/arrival TERMINAL IDS resolved from the
     * spoken place names against the terminal master (301 = 광주 공항, 251 = 군산 공항 in their
     * flight sample).
     * <p>A name we cannot resolve leaves the ids null and keeps the plain {@code depart}/{@code
     * arrival} text — which the provider accepts, so an unknown station never blocks the receipt.
     * Rail stations outside the terminal master use a separate {@code departNodeId} ("NAT…") code
     * that this list does not carry; those stay null too rather than be invented.
     */
    private void enrichTransportDetail(ObjectNode detail, String seatGrade, String token) {
        if (detail == null || !detail.hasNonNull("vehicleType")) {
            return;
        }
        String vehicle = detail.path("vehicleType").asText("");
        // A hired car has no route, no seat and no terminal: the provider's own RENTAL sample
        // carries routeType, seatClass, departTerminalId, arrivalTerminalId, departNodeId and
        // arrivalNodeId ALL null, with depart/arrival as plain place names ("Seoul" -> "Busan").
        // Returning here keeps them null and skips two catalogue lookups that could only ever
        // mismatch - there is no rental terminal to find.
        if (LOCATOR_FREE_VEHICLES.contains(vehicle)) {
            log.info("{} carries no route/seat/terminal fields - leaving them null per the sample.",
                    vehicle);
            return;
        }
        if (!detail.hasNonNull("routeType")) {
            detail.put("routeType", "ONEWAY");   // every transport sample is one-way
        }
        // Seat grade. Buses carry all three (UI 일반/우등/프리미엄 -> wire Standard/Superior/Premium);
        // a flight or a train has one fixed value and no grade to choose, so a grade named against
        // those is ignored rather than forced into their enum. The provider does NOT validate this
        // field — it stored a deliberately bogus value verbatim — so an unknown grade must stay
        // empty rather than be guessed, or the receipt carries a code nothing recognises.
        if (!detail.hasNonNull("seatClass")) {
            String grade = seatGrade == null ? "" : seatGrade.trim().toUpperCase(java.util.Locale.ROOT);
            String graded = BUS_SEAT_GRADES.get(grade);
            if (graded != null && BUS_SEAT_GRADES.containsKey("STANDARD")
                    && SEAT_CLASS_BY_VEHICLE.containsKey(vehicle) && isBus(vehicle)) {
                detail.put("seatClass", graded);
            } else {
                String seat = SEAT_CLASS_BY_VEHICLE.get(vehicle);
                if (seat != null) {
                    detail.put("seatClass", seat);   // the vehicle's only/default grade
                }
                if (!grade.isEmpty() && graded == null) {
                    log.info("Seat grade {} is not one of the three bus grades — using {} for {}.",
                            grade, seat, vehicle);
                }
            }
        }
        String fromName = detail.path("depart").asText("");
        String toName = detail.path("arrival").asText("");

        // Two locator systems, and a place lives in one or the other. The terminal master holds the
        // big hubs (13 airports, 244 rail stations, 158 bus terminals) and is addressed by numeric
        // id; the node list holds everything else - 2117 intercity-bus stops, 347 rail stations
        // including the small ones - and is addressed by a string code. Try the master first
        // because a numeric terminal id is what the provider's AIR sample uses, then fall back to
        // the node code, which is what their CBUS and KTX samples use.
        try {
            JsonNode terminals = bizplayGatewayService.getEtcCardTerminals(token);
            Long from = terminalId(terminals, vehicle, fromName);
            Long to = terminalId(terminals, vehicle, toName);
            if (from != null) {
                detail.put("departTerminalId", from);
            }
            if (to != null) {
                detail.put("arrivalTerminalId", to);
            }
        } catch (Exception e) {
            log.warn("Terminal master lookup failed ({}); trying the node list.", e.getMessage());
        }
        boolean needFrom = !detail.hasNonNull("departTerminalId") && !fromName.isBlank();
        boolean needTo = !detail.hasNonNull("arrivalTerminalId") && !toName.isBlank();
        if (!needFrom && !needTo) {
            return;
        }
        try {
            JsonNode nodes = bizplayGatewayService.getVehicleNodes(vehicle, token);
            if (needFrom) {
                String id = nodeId(nodes, fromName);
                if (id != null) {
                    detail.put("departNodeId", id);
                    detail.put("depart", nodeName(nodes, id, fromName));   // the provider's own label
                }
            }
            if (needTo) {
                String id = nodeId(nodes, toName);
                if (id != null) {
                    detail.put("arrivalNodeId", id);
                    detail.put("arrival", nodeName(nodes, id, toName));
                }
            }
        } catch (Exception e) {
            log.warn("Node lookup failed for {} ({}); depart/arrival stay as plain names.",
                    vehicle, e.getMessage());
        }
    }

    /**
     * A spoken place name -> its node code, searched IN CODE over the cached node list. The list
     * runs to thousands of stops, so it is never handed to the model: exact match first, then the
     * generic-word-stripped form, then containment, and only when exactly one candidate contains
     * the name - an ambiguous "\uac15\ub989" that matches nine stops resolves to nothing rather than
     * the wrong one.
     */
    private String nodeId(JsonNode nodes, String name) {
        if (nodes == null || !nodes.isArray() || name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim().toLowerCase(java.util.Locale.ROOT);
        String wantedCore = placeCore(wanted);
        String coreHit = null;
        String widerHit = null;   // the catalog name CONTAINS what the user said - the strong case
        int widerCount = 0;
        String narrowerHit = null;   // the user said MORE than the catalog name - the weak case
        int narrowerCount = 0;
        for (JsonNode n : nodes) {
            String candidate = n.path("nodeName").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.equals(wanted)) {
                return n.path("nodeId").asText(null);
            }
            if (coreHit == null && !wantedCore.isEmpty() && wantedCore.equals(placeCore(candidate))) {
                coreHit = n.path("nodeId").asText(null);
            }
            // Direction matters. The catalog writes "131 금호리조트(봉개)" while people say
            // "금호리조트(봉개)", so the catalog name containing the spoken one is a real match.
            // The reverse - a two-character stop like "금호" sitting inside what they said - is
            // noise, and counting both together made every specific stop look ambiguous.
            if (candidate.contains(wanted)) {
                widerCount++;
                if (widerHit == null) {
                    widerHit = n.path("nodeId").asText(null);
                }
            } else if (wanted.contains(candidate)) {
                narrowerCount++;
                if (narrowerHit == null) {
                    narrowerHit = n.path("nodeId").asText(null);
                }
            }
        }
        if (coreHit != null) {
            return coreHit;
        }
        if (widerCount == 1) {
            return widerHit;
        }
        // Still ambiguous ("금호리조트" matches both 봉개 and 교래) - resolve nothing rather than
        // guess a stop the traveller never went to; the receipt keeps the plain name and still issues.
        return (widerCount == 0 && narrowerCount == 1) ? narrowerHit : null;
    }

    /** The provider's own label for a node id, so the saved depart/arrival matches their catalog. */
    private String nodeName(JsonNode nodes, String id, String fallback) {
        for (JsonNode n : nodes) {
            if (id.equals(n.path("nodeId").asText(null))) {
                String label = n.path("nodeName").asText("");
                return label.isBlank() ? fallback : label;
            }
        }
        return fallback;
    }

    /**
     * A spoken place name -> its terminal id for that vehicle. The endpoint ignores its
     * vehicleType query and returns all 415 terminals tagged with their own, so the filter is
     * applied here. Matching is exact first, then containment either way ("Gimpo" ~ "김포 공항"
     * fails, but "광주" ~ "광주 공항" works) — null when nothing matches cleanly.
     */
    private Long terminalId(JsonNode terminals, String vehicleType, String name) {
        if (terminals == null || !terminals.isArray() || name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim().toLowerCase(java.util.Locale.ROOT);
        String wantedCore = placeCore(wanted);
        Long contains = null;
        Long core = null;
        for (JsonNode t : terminals) {
            if (!vehicleType.equalsIgnoreCase(t.path("vehicleType").asText(""))) {
                continue;
            }
            String candidate = t.path("name").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.equals(wanted)) {
                return t.path("id").asLong();
            }
            if (contains == null && (candidate.contains(wanted) || wanted.contains(candidate))) {
                contains = t.path("id").asLong();
            }
            // "김포 공항" vs the master's "김포 국제공항": the place name matches, the generic
            // wording does not. Compare what is left once the generic words are stripped.
            if (core == null && !wantedCore.isEmpty() && wantedCore.equals(placeCore(candidate))) {
                core = t.path("id").asLong();
            }
        }
        return contains != null ? contains : core;
    }

    /** The plans in this list that carry no settlement yet (usageCnt 0) — what "still to settle" means. */
    /** The first {@code limit} rows, or the array itself when it already fits. */
    private ArrayNode trimTo(ArrayNode rows, int limit) {
        if (rows.size() <= limit) {
            return rows;
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (int i = 0; i < limit; i++) {
            out.add(rows.get(i));
        }
        return out;
    }

    private ArrayNode unsettledOf(ArrayNode approved) {
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode c : approved) {
            if (c.path("usageCnt").asInt(0) == 0) {
                out.add(c);
            }
        }
        return out;
    }

    /** A place name minus the words every station carries: 김포 국제공항 / 김포 공항 -> "김포". */
    private String placeCore(String name) {
        return name.replace("국제", "")
                .replace("공항", "")
                .replace("터미널", "")
                .replace("역", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * Does this settlement actually carry an expense? Evidence arrives by two routes — a receipt
     * attached from the stream (link + snapshot layers) or one created here (the creation array) —
     * so both count. An attach can be refused (a receipt with no ISSUED child) without the user
     * noticing, and BizPlay will happily accept a body with no lines: the result is a ₩0 document
     * that means nothing to the approver.
     */
    private boolean hasEvidence(ArrayNode documents) {
        if (documents == null || documents.isEmpty()) {
            return false;
        }
        JsonNode doc = documents.get(0);
        return !doc.path("bstrReceipts").isEmpty()
                || !doc.path("receiptIds").isEmpty()
                || !doc.path("etcReceiptSaveRequests").isEmpty();
    }

    /** TranKind master, or an empty array — the slip's account subject is looked up in it. */
    private JsonNode tranKindList(String token) {
        try {
            return bizplayGatewayService.getTranKindList(token);
        } catch (Exception e) {
            log.warn("TranKind list failed ({}); the slip will carry no account subject.", e.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    /** The cost centre for the slip: the drafter's first authorized budget department. */
    private JsonNode firstBudgetDepartment(String token) {
        try {
            JsonNode depts = bizplayGatewayService.getBudgetDepartments(
                    Long.parseLong(bizplayProperties.getDefaultCorpUserId().trim()), token);
            return (depts != null && depts.isArray() && !depts.isEmpty()) ? depts.get(0) : null;
        } catch (Exception e) {
            log.warn("Budget-department lookup failed ({}); the slip will carry no cost centre.", e.getMessage());
            return null;
        }
    }

    /**
     * An attached receipt reshaped into the flat entry {@link #minimalSlip} reads, so the attach
     * path and the manual-expense path build the slip through exactly one piece of code.
     */
    private ObjectNode slipEntry(JsonNode c, JsonNode child) {
        double approval = c.path("approvalAmount").asDouble(0);
        ObjectNode e = objectMapper.createObjectNode();
        e.put("approvalAmount", firstNumber(child.path("issuedAmt"), c.path("approvalAmount"), approval));
        e.put("vatAmount", firstNumber(child.path("vatAmt"), c.path("vatAmount"), 0));
        e.put("supplyAmount", firstNumber(child.path("splAmt"), c.path("supplyAmount"), approval));
        e.put("approvalDate", c.path("approvalDate").asText(""));
        e.put("tranKindId", firstNumber(child.path("tranKindId"), c.path("tranKindId"), 0));
        e.put("mestName", c.path("mestName").asText(""));
        return e;
    }

    /**
     * receipt PK -> its ISSUED child, via GET /api/v2/receipt/issued/bulk/{ids}. One call for the
     * whole batch; a receipt with no child is simply absent from the map.
     * <p>This lookup is NOT optional. {@code receiptIds[]} and {@code issuedReceiptDtos[].id} must
     * carry the ISSUED child PK (provider answer 260810 §5-3) and the not-attached stream does not
     * carry it: {@code issuedReceiptId} is null there, {@code issuedReceiptDtos} is empty, and
     * {@code issuedReceiptIds} — despite the name — holds a TRANKIND id (11719 = 교통비). Sending
     * the receipt's own PK instead is what BizPlay rejects with HTTP 400
     * "발급된 영수증이 존재하지 않습니다".
     */
    private java.util.Map<Long, JsonNode> issuedChildren(List<JsonNode> receipts, String token) {
        java.util.Map<Long, JsonNode> byReceipt = new java.util.LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (JsonNode c : receipts) {
            long id = c.path("id").asLong();
            if (id > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return byReceipt;
        }
        try {
            JsonNode issued = bizplayGatewayService.getIssuedReceiptsBulk(ids, token);
            if (issued != null && issued.isArray()) {
                for (JsonNode ir : issued) {
                    long parent = ir.path("receiptId").asLong();
                    if (parent > 0 && ir.hasNonNull("id")) {
                        byReceipt.putIfAbsent(parent, ir);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Issued-receipt lookup failed for {} ({}); nothing will be attached.",
                    ids, e.getMessage());
        }
        return byReceipt;
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
        // BOTH evidence arrays, the same pair recomputeTotals() sums. Counting only
        // bstrReceipts reported "0 receipt(s), total ₩627000" on a settlement whose three
        // lines were all manually registered - card receipts land in bstrReceipts, manual
        // ones in etcReceiptSaveRequests, and a settlement can be made of either or both.
        int n = doc.path("bstrReceipts").size() + doc.path("etcReceiptSaveRequests").size();
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
            options.add(planOption(c, ko));
        }
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("PLAN").name(t(ko, "trip to settle", "정산할 출장")).options(options).build());
    }

    /**
     * One plan as a pickable option. label = the one-line form (old clients / plain-text log);
     * meta = the columns the chat UI lays out as a table: 목적 · 제목 · 문서번호 · 기간 · 기안자 · 등록자.
     */
    private TripPlanAgentResponse.Option planOption(JsonNode c, boolean ko) {
        java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
        meta.put("purpose", c.path("purpose").asText(""));
        meta.put("title", c.path("title").asText(""));
        meta.put("docNo", c.path("docNo").asText(""));
        meta.put("startDate", c.path("startDate").asText(""));
        meta.put("endDate", c.path("endDate").asText(""));
        meta.put("drafter", c.path("drafter").asText(""));
        meta.put("registrar", c.path("registrar").asText(""));
        meta.put("usageCnt", String.valueOf(c.path("usageCnt").asInt(0)));
        meta.put("created", c.path("created").asText(""));
        return TripPlanAgentResponse.Option.builder()
                .label(c.path("docNo").asText("") + " · " + c.path("title").asText("")
                        + " · " + c.path("startDate").asText("") + "~" + c.path("endDate").asText("")
                        + (c.path("purpose").asText("").isEmpty() ? "" : " · " + c.path("purpose").asText("")))
                .sendText("settle-plan:" + c.path("approvalId").asLong())
                .meta(meta)
                .build();
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
        // DEMO SCOPE: only 기타증빙 (ETC) is exercised for now — the other card types are
        // commented out (kept for reference, do not delete) so the flow is tested on one path.
        List<TripPlanAgentResponse.Option> options = List.of(
                // TripPlanAgentResponse.Option.builder()
                //         .label(t(ko, "Corporate card", "법인카드")).sendText("card-types:CORP").build(),
                // TripPlanAgentResponse.Option.builder()
                //         .label(t(ko, "Personal card", "개인카드")).sendText("card-types:PERSONAL").build(),
                // TripPlanAgentResponse.Option.builder()
                //         .label(t(ko, "Personal + MyData", "개인카드+마이데이터"))
                //         .sendText("card-types:PERSONAL,MY_DATA").build(),
                // 기타증빙 (ETC): where manually-registered / etc-card receipts live — the ones the
                // settlement flow creates. Without this, picking any card type misses them entirely.
                TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Other receipts (기타증빙)", "기타증빙")).sendText("card-types:ETC").build());
                // TripPlanAgentResponse.Option.builder()
                //         .label(t(ko, "All", "전체"))
                //         .sendText("card-types:CORP,PERSONAL,MY_DATA,ETC").build());
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("CARD_TYPE").name(t(ko, "card types", "카드 종류")).options(options).build());
    }

    /** After import: TranKind chips when the plan carries them, else jump to card-type selection. */
    /**
     * What to offer once a plan is imported: its expense types. A paper that pins none used to fall
     * through to the card-type question, which let a receipt be registered with no kind — created
     * NOT ISSUED and impossible to attach. There is nothing safe to offer in that case, so the
     * caller says why instead (see {@link #formPinsNoKinds}).
     */
    private List<TripPlanAgentResponse.PendingChoice> afterImportChips(ObjectNode state, boolean ko) {
        return slots(state).withArray("planTranKinds").isEmpty() ? null : tranKindChips(state, ko);
    }

    /** True when the imported plan's form lists no expense types — a form misconfiguration. */
    private boolean formPinsNoKinds(ObjectNode state) {
        return slots(state).withArray("planTranKinds").isEmpty();
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
        return submitChips(ko, null);
    }

    /**
     * The draft is ready: ask WHO approves it (roster minus the drafter) and offer submit.
     * Picking a name sets the approver for this settlement; submitting without picking falls back
     * to the configured default approver.
     */
    private List<TripPlanAgentResponse.PendingChoice> submitChips(boolean ko, String bizplayToken) {
        List<TripPlanAgentResponse.PendingChoice> groups = new ArrayList<>();
        List<TripPlanAgentResponse.Option> people = new ArrayList<>();
        long drafter = 0;
        try {
            drafter = Long.parseLong(bizplayProperties.getDefaultCorpUserId());
        } catch (RuntimeException ignored) {
            drafter = 0;
        }
        try {
            Long corpId = corporationIdFromToken(bizplayToken);
            JsonNode roster = corpId == null ? null
                    : bizplayGatewayService.getCorporationUsers(corpId, bizplayToken);
            JsonNode users = roster == null ? null : (roster.has("users") ? roster.get("users") : roster);
            if (users != null && users.isArray()) {
                for (JsonNode u : users) {
                    long uid = u.path("corporationUserId").asLong();
                    if (uid <= 0 || uid == drafter || people.size() >= 8) {
                        continue;
                    }
                    String dept = u.path("departments").path(0).path("departmentName").asText("");
                    String pos = u.path("positionName").asText("");
                    people.add(TripPlanAgentResponse.Option.builder()
                            .label(u.path("userName").asText("?")
                                    + (dept.isEmpty() ? "" : " · " + dept)
                                    + (pos.isEmpty() ? "" : " · " + pos))
                            .sendText("approver:" + uid)
                            .build());
                }
            }
        } catch (Exception e) {
            log.info("Approver roster unavailable ({}) - submit uses the default approver.", e.getMessage());
        }
        if (!people.isEmpty()) {
            groups.add(TripPlanAgentResponse.PendingChoice.builder()
                    .kind("APPROVER").name(t(ko, "who approves this?", "누가 결재하나요?"))
                    .options(people).build());
        }
        groups.add(TripPlanAgentResponse.PendingChoice.builder()
                .kind("SUBMIT").name(t(ko, "submit", "제출"))
                .options(List.of(TripPlanAgentResponse.Option.builder()
                        .label(t(ko, "Submit to BizPlay", "BizPlay에 제출"))
                        .sendText("submit").build()))
                .build());
        return groups;
    }

    private List<TripPlanAgentResponse.PendingChoice> receiptChips(ObjectNode state, ArrayNode documents, boolean ko) {
        return receiptChipsFrom((ArrayNode) state.withArray("receiptCandidates"),
                documents.isEmpty() ? null : documents.get(0), ko);
    }

    private List<TripPlanAgentResponse.PendingChoice> receiptChipsFrom(ArrayNode candidates, JsonNode doc, boolean ko) {
        // Each receipt option carries its fields in meta so the UI renders a TABLE (one row + Attach
        // button per receipt). No "attach all" — receipts are attached one at a time from the table.
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (JsonNode c : candidates) {
            if (alreadyAttached(doc, c.path("id").asLong())) {
                continue;
            }
            java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
            meta.put("id", String.valueOf(c.path("id").asLong()));
            meta.put("mestName", c.path("mestName").asText(""));
            meta.put("approvalDate", c.path("approvalDate").asText(""));
            meta.put("approvalAmount", c.path("approvalAmount").asText("0"));
            meta.put("cardType", c.path("cardType").asText(""));
            meta.put("tranKindType", c.path("tranKindType").asText(c.path("tranKindNames").asText("")));
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(c.path("mestName").asText("") + " · " + c.path("approvalDate").asText("")
                            + " · ₩" + c.path("approvalAmount").asText("0")
                            + " · " + c.path("cardType").asText(""))
                    .sendText("receipt:" + c.path("id").asLong())
                    .meta(meta)
                    .build());
            if (options.size() >= CHIP_LIMIT) {
                break;
            }
        }
        // Action options (no meta) — the UI renders these as chips below the table.
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
    /** yyyy-MM-dd, and the ways people actually type it: 2026.08.25, 2026/08/25, 2026 08 25. */
    private static final Pattern LOOSE_DATE = Pattern.compile(
            "\\b(\\d{4})[-./\\s](\\d{1,2})[-./\\s](\\d{1,2})\\b");

    /** Every date in the message, in the order written. Impossible dates are skipped. */
    private List<String> looseDates(String message) {
        List<String> out = new ArrayList<>();
        if (message == null || message.isBlank()) {
            return out;
        }
        Matcher m = LOOSE_DATE.matcher(message);
        while (m.find()) {
            try {
                out.add(LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))).toString());
            } catch (RuntimeException ignored) {
                // 2026-13-40 is text that looks like a date but is not one.
            }
        }
        return out;
    }

    private String[] extractPeriod(String message) {
        List<String> dates = looseDates(message);
        if (dates.size() >= 2) {
            return new String[]{dates.get(0), dates.get(1)};
        }
        if (dates.size() == 1) {
            // One date means THAT DAY, not "no period given". Returning null here left the
            // previous period sitting in the slot bag, so "find me the plan at 2026-08-25" re-ran
            // the old 08-02~08-03 search and answered with the old dates - three times running.
            return new String[]{dates.get(0), dates.get(0)};
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
            types.add("BZP_POINT");   // the provider pairs the two in every receipt-list sample
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
        // NO early return when the paper pins nothing: that is exactly the case the master
        // fallback below exists for. Bailing out here left planTranKinds empty, the flow never
        // asked which expense type it was, and the receipt was then registered without one —
        // which BizPlay creates NOT ISSUED and refuses to attach.
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
        // RETIRED (kept for reference, do not delete): a fallback that loaded the corp's whole
        // TranKind master whenever a paper pinned none, so the flow still had something to offer.
        // It filled the gap but broke the rule that the FORM decides which kinds a trip may claim -
        // it offered kinds like 취소수수료 or 테스트 숙박비 on plans whose paper never sanctioned them.
        // A paper that pins nothing is a configuration error and now reads as one; the settlement
        // says so and stops rather than inventing a catalogue. (Reference: paper 24354 pins its
        // kinds on the 사전출장(노출) item; paper 27803 has no such item.)
        //     if (byId.isEmpty()) { for (JsonNode tk : bizplayGatewayService.getTranKindList(token)) … }
        if (byId.isEmpty()) {
            log.warn("Paper {} on plan {} pins no TranKinds - receipt registration cannot proceed "
                            + "until the form lists its expense types.",
                    planDetail.path("paper").path("id").asLong(),
                    planDetail.path("docNo").asText(""));
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
        boolean periodThisTurn = period != null;
        if (periodThisTurn) {
            s.put("startDate", period[0]);
            s.put("endDate", period[1]);
        }
        List<String> words = parseCardWords(message);
        if (!words.isEmpty()) {
            ArrayNode ct = s.putArray("cardTypes");
            words.forEach(ct::add);
        }
        mergeExtracted(s, extracted, periodThisTurn);
    }

    /** Merge the LLM-extracted slots, filling only what the deterministic parsers left empty. */
    private void mergeExtracted(ObjectNode s, JsonNode ex, boolean periodThisTurn) {
        if (ex == null || !ex.isObject()) {
            return;
        }
        // The old guard was "only if no period is stored", which meant the FIRST period a session
        // ever saw could never be replaced - every later date the user typed was ignored and the
        // original search ran again. What must not be overwritten is a period parsed
        // deterministically from THIS message; anything older is stale by definition.
        if (!periodThisTurn && ex.hasNonNull("startDate") && ex.hasNonNull("endDate")) {
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

    /**
     * The chat prepends the site's language choice ("Respond in Korean only…") — that switch owns
     * the reply language for the whole system, so it wins over what the user typed in. Hangul
     * proportion is only the fallback for callers that send no marker (API clients, tests).
     */

    /**
     * Keys the provider's EtcReceiptSaveRequest DTO actually knows — the captured sample's key
     * set plus the purpose-specific fields it documents. Anything else in the body is a hard 400
     * ("Unrecognized field ... not marked as ignorable", 260810 answer 3-3.3): the created
     * receipt's server echo (confirmIssued) drags in fields like expenseStatus that must never
     * reach the save call.
     */
    private static final java.util.Set<String> ETC_SAVE_DTO_KEYS = java.util.Set.of(
            "approvalAmount", "approvalCanceled", "approvalDate", "arrivalTerminalId", "bldat",
            "bstrPayClassType", "currencyCode", "departTerminalId", "displayHidden",
            "documentBound", "etcReceiptType", "exchangeRate", "issuedItems", "mestName",
            "nonDeductionCustom", "reqAmt", "ruledAmount", "slip", "supplyAmount", "tranKindId",
            "tranKindType", "usedEndDate", "usedStartDate", "vatAmount", "receiptId", "imageIds",
            "cancelReason", "outPolicyReason", "vehicleType", "routeType", "seatClass",
            "depart", "arrival", "departNodeId", "arrivalNodeId", "starRating", "roomType",
            "partnerHotel", "personCount", "foodDivisionType", "overseasUsed",
            "overseasApprovalAmount", "overseasRuledAmount");

    /** Strip every key the save DTO doesn't know from each etcReceiptSaveRequests entry,
     *  and make sure each carries the MANDATORY slip (전표 정보는 필수입니다 otherwise):
     *  amounts from the entry itself, the account from the TranKind's debit fields —
     *  the minimal-viable slip. */
    private void sanitizeEtcSaveRequests(ArrayNode documents, String bizplayToken) {
        sanitizeEtcSaveRequests(documents, bizplayToken, 0);
    }

    /** @param pickedApprover approver chosen in the chat; 0 = fall back to the configured default. */
    private void sanitizeEtcSaveRequests(ArrayNode documents, String bizplayToken, long pickedApprover) {
        JsonNode tranKinds = null;
        JsonNode budgetDept = null;   // first usable 코스트센터 — mandatory in every slip
        for (JsonNode dnode : documents) {
            if (!(dnode instanceof ObjectNode dn)) {
                continue;
            }
            // 4-3.2: expense reports never rewrite trip type / pay type — fixed null on save.
            dn.putNull("bstrType");
            dn.putNull("bstrPayType");
            ensureApprover(dn, bizplayToken, pickedApprover);
            fillApprovalLineDepartments(dn, bizplayToken);
            ArrayNode etc = dn.withArray("etcReceiptSaveRequests");
            ArrayNode kept = objectMapper.createArrayNode();
            for (JsonNode n : etc) {
                if (!(n instanceof ObjectNode entry)) {
                    continue;
                }
                if (tranKinds == null) {
                    try {
                        tranKinds = bizplayGatewayService.getTranKindList(bizplayToken);
                    } catch (Exception e) {
                        tranKinds = objectMapper.createArrayNode();
                    }
                    JsonNode depts = bizplayGatewayService.getBudgetDepartments(
                            Long.parseLong(bizplayProperties.getDefaultCorpUserId()), bizplayToken);
                    budgetDept = depts.isArray() && !depts.isEmpty() ? depts.get(0) : null;
                }
                long rid = entry.path("receiptId").asLong(0);
                long iid = entry.path("issuedReceiptId").asLong(0);
                if (rid > 0 && iid > 0) {
                    // Pre-created upstream (etc-card POST already ran): a receipt WITH a PK belongs
                    // in the link/snapshot layers, never the creation array (260810 answer 3-2) —
                    // leaving it here makes the server try to create it AGAIN.
                    addReceiptIdOnce(dn, iid);
                    ObjectNode f = issuedField(entry, rid, iid, dn);
                    ObjectNode dto = (ObjectNode) f.withArray("issuedReceiptDtos").get(0);
                    dto.set("slip", minimalSlip(entry, tranKinds, budgetDept));
                    dn.withArray("issuedFields").add(f);
                    // Carry the receipt's own identity onto the issued dto — the working save
                    // (and the browser capture) has these on every dto.
                    dto.put("tranKindId", entry.path("tranKindId").asLong());
                    dto.put("tranKindType", entry.path("tranKindType").asText(""));
                    dto.put("mestName", entry.path("mestName").asText(""));
                    dto.put("approvalDate", entry.path("approvalDate").asText(null));

                    ObjectNode row = bstrReceipt(entry, rid, iid);
                    double amt = entry.path("approvalAmount").asDouble(0);
                    double vat = entry.hasNonNull("vatAmount") ? entry.path("vatAmount").asDouble(0) : 0;
                    row.put("slipAmt", amt);
                    row.put("slipSplAmt", amt - vat);
                    row.put("slipVatAmt", vat);
                    JsonNode slipForRow = dto.path("slip");
                    for (String k : new String[]{"accountSubjectId", "accountSubjectName",
                            "accountSubjectErpCode", "budgetDepartmentId", "budgetDepartmentName",
                            "budgetDepartmentErpCode", "summary"}) {
                        if (slipForRow.hasNonNull(k)) {
                            row.set(k, slipForRow.get(k).deepCopy());
                        }
                    }
                    // THE row was built but never attached — without it the save has no snapshot
                    // layer and the portal fails with an opaque error.
                    dn.withArray("bstrReceipts").add(row);
                    continue;
                }
                java.util.List<String> drop = new java.util.ArrayList<>();
                entry.fieldNames().forEachRemaining(k -> {
                    if (!ETC_SAVE_DTO_KEYS.contains(k)) {
                        drop.add(k);
                    }
                });
                entry.remove(drop);
                if (!entry.hasNonNull("slip")) {
                    entry.set("slip", minimalSlip(entry, tranKinds, budgetDept));
                }
                kept.add(entry);
            }
            dn.set("etcReceiptSaveRequests", kept);
            // The live capture's COST_CENTER item carries the picked cost center as value +
            // selection; ours was empty even though the slip had it — fill both the same way.
            if (budgetDept != null) {
                for (JsonNode itn : dn.withArray("issuedItems")) {
                    if (itn instanceof ObjectNode it
                            && "COST_CENTER".equals(it.path("item").path("itemType").asText())) {
                        it.put("value", budgetDept.path("name").asText(""));
                        ArrayNode sels = it.putArray("selections");
                        ObjectNode sel = sels.addObject();
                        sel.set("selectionId", budgetDept.get("id").deepCopy());
                        sel.put("selectionName", budgetDept.path("name").asText(""));
                        sel.put("selectionErpCode", budgetDept.path("erpCode").asText(""));
                        sel.putNull("selectionMemo");
                        sel.putNull("selectionAreaInfo");
                    }
                }
            }
        }
    }

    private void addReceiptIdOnce(ObjectNode doc, long issuedReceiptId) {
        for (JsonNode existing : doc.withArray("receiptIds")) {
            if (existing.asLong() == issuedReceiptId) {
                return;
            }
        }
        doc.withArray("receiptIds").add(issuedReceiptId);
    }

    /** slipAmt = total, slipSplAmt = supply (or the whole total), slipVatAmt = VAT (or 0);
     *  account subject from the TranKind's debit account; everything else null. */
    private ObjectNode minimalSlip(ObjectNode entry, JsonNode tranKinds, JsonNode budgetDept) {
        double amt = entry.path("approvalAmount").asDouble(0);
        double vat = entry.hasNonNull("vatAmount") ? entry.path("vatAmount").asDouble(0) : 0;
        double spl = entry.hasNonNull("supplyAmount") ? entry.path("supplyAmount").asDouble(amt - vat) : amt - vat;
        ObjectNode slip = objectMapper.createObjectNode();
        // 4-3.3: the server resolves the posting date (budat) from slip.docDate first, then the
        // POSTING_DATE item — this paper HAS no POSTING_DATE item, so docDate is the only path.
        // It must NOT precede the evidence date ("증빙일은 전기일 보다 이전이어야 합니다"), which
        // happens whenever the trip is in the future — so take the later of today and the receipt.
        String today = java.time.LocalDate.now().toString();
        String evidence = entry.path("approvalDate").asText("");
        slip.put("docDate", evidence.compareTo(today) > 0 ? evidence : today);
        slip.put("slipAmt", amt);
        slip.put("slipSplAmt", spl);
        slip.put("slipVatAmt", vat);
        long tkId = entry.path("tranKindId").asLong(0);
        for (JsonNode tk : (tranKinds != null && tranKinds.isArray()) ? tranKinds : objectMapper.createArrayNode()) {
            if (tk.path("id").asLong() == tkId && tk.hasNonNull("debitId")) {
                slip.set("accountSubjectId", tk.get("debitId").deepCopy());
                slip.put("accountSubjectName", tk.path("debitName").asText(""));
                slip.put("accountSubjectErpCode", tk.path("debitCode").asText(""));
                break;
            }
        }
        if (budgetDept != null) {
            slip.set("budgetDepartmentId", budgetDept.get("id").deepCopy());
            slip.put("budgetDepartmentName", budgetDept.path("name").asText(""));
            slip.put("budgetDepartmentErpCode", budgetDept.path("erpCode").asText(""));
        }
        // Per the LIVE browser capture of a successful save (2026-08-18): taxCodeId/taxName are
        // null on this corp's slips; branchOfficeId is 905. (An earlier guess of 21/불공제/5 from
        // the doc sample was wrong — that sample is another corp's data.)
        slip.putNull("taxCodeId");
        slip.putNull("taxName");
        slip.put("branchOfficeId", 905);
        slip.put("summary", entry.path("mestName").asText(""));
        return slip;
    }


    /**
     * The live capture's approval lines all carry the member's departmentId; ours had null on the
     * DRAFT line. Fill any null departmentId from the corp roster (main department of that user).
     */

    /** Read currentCorpId from the (unverified) JWT payload — enough for a roster lookup key. */
    private Long corporationIdFromToken(String token) {
        String jwt = (token != null && !token.isBlank()) ? token : bizplayProperties.getDevToken();
        if (jwt == null || jwt.isBlank()) {
            return null;
        }
        try {
            String[] parts = jwt.trim().split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            long id = objectMapper.readTree(payload).path("currentCorpId").asLong(0);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * A settlement needs someone to approve it: after the drafter's DRAFT line, add the configured
     * approver (김도하 by default) unless the user already picked an approval line themselves.
     * departmentId is filled right after by fillApprovalLineDepartments().
     */

    /** Display name for an approver id - cosmetic, the id is what matters. */
    private String approverName(long corpUserId, String bizplayToken) {
        try {
            Long corpId = corporationIdFromToken(bizplayToken);
            JsonNode roster = corpId == null ? null
                    : bizplayGatewayService.getCorporationUsers(corpId, bizplayToken);
            JsonNode users = roster == null ? null : (roster.has("users") ? roster.get("users") : roster);
            for (JsonNode u : users != null && users.isArray() ? users : objectMapper.createArrayNode()) {
                if (u.path("corporationUserId").asLong() == corpUserId) {
                    return u.path("userName").asText("");
                }
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private void ensureApprover(ObjectNode doc, String bizplayToken, long pickedApproverId) {
        ArrayNode lines = doc.withArray("approvalLines");
        long approverId = pickedApproverId;
        if (approverId <= 0) {
            try {
                approverId = Long.parseLong(bizplayProperties.getDefaultApproverId());
            } catch (RuntimeException e) {
                return;
            }
        }
        int order = 0;
        for (JsonNode l : lines) {
            if (!"DRAFT".equals(l.path("approvalKindType").asText())) {
                return;   // the user already chose approvers — never override their picks
            }
            order = Math.max(order, l.path("approvalOrder").asInt(0));
            if (l.path("corporationUserId").asLong() == approverId) {
                return;
            }
        }
        ObjectNode approver = lines.addObject();
        approver.put("approvalKindType", "APPROVAL");
        approver.put("approvalOrder", order + 1);
        approver.put("corporationUserId", approverId);
        approver.putNull("departmentId");
        approver.put("departmentApproval", false);
        approver.put("paperApprovalLineType1", "EMPLOYEE");
        log.info("Settlement approval line: added approver corpUserId={}", approverId);
    }

    private void fillApprovalLineDepartments(ObjectNode doc, String bizplayToken) {
        JsonNode roster = null;
        for (JsonNode l : doc.withArray("approvalLines")) {
            if (!(l instanceof ObjectNode line) || line.hasNonNull("departmentId")) {
                continue;
            }
            long uid = line.path("corporationUserId").asLong(0);
            if (uid <= 0) {
                continue;
            }
            if (roster == null) {
                try {
                    Long corpId = corporationIdFromToken(bizplayToken);
                    roster = corpId == null ? objectMapper.createArrayNode()
                            : bizplayGatewayService.getCorporationUsers(corpId, bizplayToken);
                } catch (Exception e) {
                    roster = objectMapper.createArrayNode();
                }
            }
            for (JsonNode u : roster.isArray() ? roster : objectMapper.createArrayNode()) {
                if (u.path("corporationUserId").asLong() == uid) {
                    for (JsonNode dep : u.path("departments")) {
                        if (dep.path("mainDepartment").asBoolean(false) || !line.hasNonNull("departmentId")) {
                            line.put("departmentId", dep.path("departmentId").asLong());
                        }
                    }
                    break;
                }
            }
        }
    }

    private boolean koreanConversation(String message) {
        if (message.contains("Respond in Korean only")) {
            return true;
        }
        if (message.contains("Respond in English only")) {
            return false;
        }
        long hangul = message.codePoints().filter(cp -> cp >= 0xAC00 && cp <= 0xD7A3).count();
        long latin = message.codePoints().filter(Character::isAlphabetic).count() - hangul;
        return hangul > 0 && hangul * 3 > latin;
    }

    private static String t(boolean ko, String en, String kr) {
        return ko ? kr : en;
    }
}
