package com.api.bizplay_conversational.service.bizplayPlanAgentService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayFormResponse;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.model.response.PurposeOption;
import com.api.bizplay_conversational.model.response.PurposeResolutionResult;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.api.bizplay_conversational.model.response.DatabaseLookupAgentResponse;
import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.api.bizplay_conversational.repository.ConversationalAgentSessionRepo;
import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.api.bizplay_conversational.service.customAgentService.CustomAgentService;
import com.api.bizplay_conversational.service.databaseLookupAgentService.DatabaseLookupAgentService;
import com.api.bizplay_conversational.service.fieldMapperAgentService.FieldMapperAgentService;
import com.api.bizplay_conversational.service.fileExtractionService.FileExtractionService;
import com.api.bizplay_conversational.service.fileExtractionService.UploadedFile;
import com.api.bizplay_conversational.service.pdfAgentService.PdfAgentService;
import com.api.bizplay_conversational.service.spreadsheetAgentService.SpreadsheetAgentService;
import com.api.bizplay_conversational.service.formFollowUpAgentService.FormFollowUpAgentService;
import com.api.bizplay_conversational.service.formSkeletonService.FormSkeletonService;
import com.api.bizplay_conversational.service.formValueWriterService.FormValueWriterService;
import com.api.bizplay_conversational.service.guardrailAgentService.GuardrailAgentService;
import com.api.bizplay_conversational.service.placeValidationService.PlaceValidationService;
import com.api.bizplay_conversational.config.BizplayProperties;
import com.api.bizplay_conversational.service.purposeSegmentAgentService.PurposeSegmentAgentService;
import com.api.bizplay_conversational.service.travelerResolverService.TravelerResolverService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrator of the BizPlay form-driven trip-plan flow.
 *
 * The session's draft_json is EXACTLY the plan-draft request body: an ARRAY of documents whose
 * structure comes 1:1 from the retrieved form (skeleton from ②) — only VALUES are updated in it,
 * never the structure. Everything the agent needs that has no slot in that body (staged messages,
 * unresolved traveler names, the destination that rides the period selections, the chosen purpose
 * label, the field spec cache key) lives in a separate agent-state entry inside chat_event_json.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizplayPlanAgentServiceImple implements BizplayPlanAgentService {

    private static final String STATE_ROLE = "agent_state";

    private final ConversationalAgentSessionRepo sessionRepo;
    private final GuardrailAgentService guardrailAgentService;
    private final PlaceValidationService placeValidationService;
    private final BizplayGatewayService bizplayGatewayService;
    private final PurposeSegmentAgentService purposeSegmentAgentService;
    private final FormSkeletonService formSkeletonService;
    private final FieldMapperAgentService fieldMapperAgentService;
    private final FormValueWriterService formValueWriterService;
    private final FormFollowUpAgentService formFollowUpAgentService;
    private final TravelerResolverService travelerResolverService;
    private final AgentPromptService agentPromptService;
    private final CustomAgentService customAgentService;
    private final DatabaseLookupAgentService databaseLookupAgentService;
    private final FileExtractionService fileExtractionService;
    private final SpreadsheetAgentService spreadsheetAgentService;
    private final PdfAgentService pdfAgentService;
    private final BizplayProperties bizplayProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken) {
        if (request.getCorpNo() == null || request.getCorpNo().isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        if (request.getCorpUserId() == null || request.getCorpUserId().isBlank()) {
            // Demo/default drafter — the document's draftUserId and row 0 come from this user.
            request.setCorpUserId(bizplayProperties.getDefaultCorpUserId());
        }
        // Sub-agents resolve their (possibly corp-customized) prompts through this context.
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(request.getCorpNo());
        try {
            return chatTurn(request, bizplayToken);
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    private BizplayPlanAgentResponse chatTurn(BizplayPlanAgentRequest request, String bizplayToken) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        List<String> fileIds = request.getFileIds() == null ? List.of() : request.getFileIds();
        if (message.isBlank() && fileIds.isEmpty()) {
            throw new IllegalArgumentException("message is required.");
        }
        boolean ko = koreanConversation(message);

        // Guardrail BEFORE any session write or LLM call: refuse DB-mutation requests aimed at
        // the NL->SQL lookup agent, prompt-injection phrasing, and oversized input. It also
        // ROUTES: a read-only data question goes to the NL->SQL lookup agent instead of the form.
        GuardrailAgentService.GuardrailResult guard = guardrailAgentService.check(message);
        if (!guard.allowed()) {
            return BizplayPlanAgentResponse.builder()
                    .sessionId(request.getSessionId())
                    .intent("GUARDRAIL_BLOCKED")
                    .subAgents(List.of("GUARDRAIL_AGENT"))
                    .reply(guard.reply())
                    .build();
        }
        // User-defined custom sub-agents get first claim on the turn (router matches the
        // message against each agent's "when to use"; null = nobody claimed it).
        // EXCEPT short mid-session messages without a question mark: those are almost
        // always answers to the form's pending question ("domestic", "부산", a name) —
        // offering them to the router misroutes them to whichever agent sounds closest.
        boolean midSession = request.getSessionId() != null && !request.getSessionId().isBlank();
        boolean shortAnswer = !message.contains("?")
                && (message.length() < 20 || message.split("\\s+").length <= 3);
        // Chip tokens are OUR OWN generated text ("Trip type: <purpose> / <segment>"), not a user
        // request — routing them can hand the turn to an unrelated custom agent whose name happens
        // to resemble the purpose (e.g. a per-diem agent claiming "귀향교통비").
        boolean chipToken = message.toLowerCase().startsWith("trip type:");
        if (!message.isBlank() && !chipToken && !(midSession && shortAnswer)) {
            CustomAgentService.RoutedReply custom = customAgentService.tryHandle(request.getCorpNo(), message);
            if (custom != null) {
                return sideTurn(request, message, custom.reply(), "CUSTOM_AGENT",
                        List.of("CUSTOM:" + custom.agentName()));
            }
        }
        // Draft Q&A: on an ACTIVE session, questions about the plan ("show me all the details",
        // "who are the travelers?", "언제 가는 걸로 돼 있어?") answer from the DRAFT itself —
        // any subset, any phrasing. Must run BEFORE the DATA_QUERY route, which would otherwise
        // dump unrelated database rows for the same questions.
        String bareMessage = stripChatContext(message);
        if (midSession && isDraftQuestion(bareMessage)) {
            ConversationalAgentSession qaSession = resolveSession(request);
            ArrayNode qaDocs = documents(qaSession);
            if (!qaDocs.isEmpty()) {
                ObjectNode slim = loadState(qaSession).deepCopy();
                // Internal bookkeeping is not user data — the summary must read like the form.
                for (String k : new String[]{"fields", "staged", "validatedDestination",
                        "validatedOrigin", "lang", "travelerIds", "pendingTravelers"}) {
                    slim.remove(k);
                }
                String context = "STATE: " + slim + "\nDOCUMENT: "
                        + truncate(qaDocs.get(0).toString(), 3500);
                String answer = formFollowUpAgentService.answerDraftQuestion(context, bareMessage, ko);
                return sideTurn(request, message, answer, "DRAFT_QUERY", List.of("FOLLOW_UP_AGENT"));
            }
        }
        if ("DATA_QUERY".equals(guard.category()) && agentPromptService.isModuleEnabled("database-lookup")) {
            return dataQueryTurn(request, message, ko);
        }

        ConversationalAgentSession session = resolveSession(request);
        ObjectNode state = loadState(session);
        ArrayNode documents = documents(session);

        String intent;
        String clarify = null;   // set when the user's input was indefinite — we ask, not guess
        List<String> subAgents = new ArrayList<>();
        List<TripPlanAgentResponse.PendingChoice> pendingChoices = null;
        StringBuilder reply = new StringBuilder();

        // Spreadsheet / PDF sub-agents: extracted file content joins this turn as extra text
        // (mapped by the same field-mapper) and traveler names queue for the roster resolver.
        String fileFacts = extractFiles(request, state, subAgents, reply, ko);
        String turnText = message.isBlank() ? fileFacts
                : (fileFacts.isBlank() ? message : message + "\n" + fileFacts);

        if (documents.isEmpty()) {
            // --- Sub-agent [A]: Purpose & Segment resolution -------------------------------------
            JsonNode catalog = bizplayGatewayService.getPurposeCatalog(request.getCorpUserId(), bizplayToken);
            List<PurposeOption> options = purposeSegmentAgentService.flattenCatalog(catalog);

            // The form asks these as two fields - Travel Purpose, then Trip Type - so the chat does
            // too. A flat row of "purpose · segment" chips forced the user to read every
            // combination at once; picking the purpose first narrows it to that purpose's own
            // trip types, and a purpose with none skips the second question entirely.
            java.util.regex.Matcher purposePick = PURPOSE_PICK.matcher(message);
            if (purposePick.matches()) {
                long purposeId = Long.parseLong(purposePick.group(1));
                List<PurposeOption> ofPurpose = new ArrayList<>();
                for (PurposeOption o : options) {
                    if (o.getPurposeId() == purposeId) {
                        ofPurpose.add(o);
                    }
                }
                if (ofPurpose.size() == 1) {
                    // No trip types on this purpose - the single option IS the answer.
                    turnText = ofPurpose.get(0).getSendText();
                } else if (!ofPurpose.isEmpty()) {
                    appendTurn(session, "user", message);
                    String ask = t(ko, "Which trip type? ", "출장 유형을 선택해 주세요. ");
                    appendTurn(session, "assistant", ask);
                    saveState(session, state);
                    ConversationalAgentSession savedSeg = sessionRepo.save(session);
                    return BizplayPlanAgentResponse.builder()
                            .sessionId(savedSeg.getId().toString())
                            .status(savedSeg.getStatus() == null ? null : savedSeg.getStatus().name())
                            .intent("SEGMENT_SELECTION")
                            .subAgents(List.of("PURPOSE_SEGMENT_AGENT"))
                            .reply(ask)
                            .pendingChoices(List.of(segmentChoice(ofPurpose)))
                            .draftJson(savedSeg.getDraftJson())
                            .build();
                }
            }

            PurposeResolutionResult res = purposeSegmentAgentService.resolve(turnText, options);
            subAgents.add("PURPOSE_SEGMENT_AGENT");

            if (res.isResolved()) {
                intent = "FORM_LOAD";
                PurposeOption chosen = res.getResolved();
                // --- Sub-agent [B]: deterministic Form Builder (② -> request-body skeleton) ------
                JsonNode papers = bizplayGatewayService.getPapers(
                        chosen.getPurposeId(), chosen.getSegmentId(), bizplayToken);
                BizplayFormResponse form = formSkeletonService.buildPlanSkeleton(
                        papers, chosen.getPurposeId(), chosen.getSegmentId());
                ObjectNode document = (ObjectNode) form.getDocument();
                setDraftUser(document, request.getCorpUserId());
                documents.add(document);
                state.set("purpose", objectMapper.valueToTree(chosen));
                state.put("paperName", form.getPaperName());
                state.set("fields", objectMapper.valueToTree(form.getFields()));
                subAgents.add("FORM_BUILDER");
                reply.append(t(ko,
                        "Great — I've started a \"" + chosen.getLabel() + "\" plan for you. ",
                        "좋아요 — \"" + chosen.getLabel() + "\" 출장 계획을 시작할게요. "));
                // Map everything said so far (staged turns + this message) onto the fresh form.
                clarify = fillFields(document, state, stagedPlus(state, turnText), subAgents, reply, ko);
                // Deterministic assist: when the purpose was inferred from a bare place answer
                // ("Busan" / "부산" to "where are you going?"), the mapper LLM sometimes misses
                // it. A short standalone token that isn't a trip-type word IS the destination.
                String answer = message.trim();
                if (!state.hasNonNull("destination") && !answer.isBlank()
                        && answer.length() <= 20 && answer.split("\\s+").length <= 2
                        && !answer.matches("(?iu).*(출장|국내|해외|일반|장기|시내|domestic|overseas|international|trip|general).*")
                        && !chosen.getLabel().contains(answer)) {
                    state.put("destination", answer);
                }
            } else {
                intent = "PURPOSE_SELECTION";
                state.withArray("staged").add(turnText);
                pendingChoices = List.of(purposeChoice(res.getCandidates(), options));
                reply.append(t(ko,
                        "What kind of trip are you planning? Pick the one that fits below — "
                                + "or just tell me where you're going and I'll pick for you. ",
                        "어떤 성격의 출장인가요? 아래에서 골라 주셔도 되고, "
                                + "목적지만 말씀해 주시면 제가 맞는 유형을 골라 드릴게요. "));
            }
        } else {
            java.util.regex.Matcher pick = TRAVELER_PICK.matcher(message);
            if (pick.matches()) {
                // Chip click from a traveler disambiguation: deterministic, no LLM involved.
                intent = "TRAVELER_PICK";
                applyTravelerPick(state, pick.group(1).trim(), Long.parseLong(pick.group(2)), pick.group(3).trim(), reply, ko);
            } else {
                // Form already loaded: every turn is field completion on documents[0].
                intent = "FIELD_COMPLETION";
                clarify = fillFields((ObjectNode) documents.get(0), state, turnText, subAgents, reply, ko);
            }
        }

        // Resolve pending traveler names against the corporation roster (gateway tool + chips).
        if (!documents.isEmpty() && agentPromptService.isModuleEnabled("traveler-resolver")) {
            List<TripPlanAgentResponse.PendingChoice> travelerChips =
                    resolveTravelers(request, state, bizplayToken, reply, subAgents, ko);
            if (pendingChoices == null && !travelerChips.isEmpty()) {
                pendingChoices = travelerChips;
            }
        }

        // Korea-only place validation: DOMESTIC (국내) trips get their destination checked
        // against the Korean gazetteer/geocoder. Non-blocking — an unknown place only warns.
        if (!documents.isEmpty() && agentPromptService.isModuleEnabled("place-validator")) {
            validateDestinationIfKorean(state, subAgents, reply, ko);
        }

        // The origin rides inside the BSTR_PERIOD value, so once the dates are complete the mapper
        // stops writing that field and a later "출발지는 인천이야" had nowhere to land - the user
        // said it twice and the draft still read origin=null. The place is sitting in the message
        // either way, so read it deterministically rather than re-asking.
        backfillOrigin(state, turnText);

        // --- Validation + Sub-agent [D]: follow-up question ---------------------------------------
        List<String> missing = List.of();
        if (!documents.isEmpty() && clarify != null) {
            // Indefinite input ("sometime next week"): ask for the exact value instead of
            // pretending the turn changed something or repeating the ready message.
            session.setStatus(ConversationalAgentSession.AgentStatus.COLLECTING);
            reply.append(clarify).append(' ');
        } else if (!documents.isEmpty()) {
            missing = requiredGaps(documents.get(0), state);
            // Before ASKING for something, re-read this turn's text for that one field: the broad
            // mapper occasionally returns nothing for a sentence that plainly contains the value
            // ("a trip to Busan next Tuesday"), and asking for what the user just said reads broken.
            missing = retryMissingField((ObjectNode) documents.get(0), state, turnText, missing, subAgents);
            if (missing.isEmpty()) {
                session.setStatus(ConversationalAgentSession.AgentStatus.READY_FOR_REVIEW);
                reply.append(t(ko,
                        "The form is all filled in — the last step is the approval line.",
                        "양식은 모두 채워졌어요 — 마지막으로 결재선만 정하면 돼요."));
            } else {
                session.setStatus(ConversationalAgentSession.AgentStatus.COLLECTING);
                // ONE required field per turn: a wall of questions makes users skip some and
                // answer others out of order. The remaining gaps stay in missingFields for the
                // UI's progress display, but the agent only ASKS for the first one. Remember
                // WHICH field, so next turn's bare answer can be bound to it deterministically.
                List<String> askNow = List.of(missing.get(0));
                rememberPendingAsk(state, missing.get(0));
                String more = missing.size() > 1
                        ? t(ko, " (" + (missing.size() - 1) + " more to go after this)",
                                " (이후 " + (missing.size() - 1) + "개 더 남았어요)")
                        : "";
                if (agentPromptService.isModuleEnabled("form-follow-up")) {
                    reply.append(formFollowUpAgentService.composeFollowUp(
                            state.path("paperName").asText(null), askNow, ko)).append(more);
                    subAgents.add("FOLLOW_UP_AGENT");
                } else {
                    // Module off: deterministic ask, no LLM call.
                    reply.append(t(ko,
                            "Could you tell me: " + askNow.get(0) + "?" + more,
                            askNow.get(0) + "을(를) 알려 주시겠어요?" + more));
                }
            }
        }

        // Remember/refresh resolved traveler ids, then fan the master document out: one object per
        // traveler, exactly like the BizPlay save body (followers lack the drafter-only keys).
        if (request.getTravelerCorpUserIds() != null && !request.getTravelerCorpUserIds().isEmpty()) {
            state.set("travelerIds", objectMapper.valueToTree(request.getTravelerCorpUserIds()));
        }
        syncTravelerDocuments(documents, state, request.getCorpUserId());

        // Persist: draft_json = EXACTLY the request-body array; state rides in chat_event_json.
        state.put("lang", ko ? "ko" : "en");   // save-flow replies reuse the conversation language
        session.setDraftJson(documents);
        appendTurn(session, "user", turnText);   // includes extracted file facts for later context
        appendTurn(session, "assistant", reply.toString());
        saveState(session, state);
        sessionRepo.save(session);
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent(intent)
                .subAgents(subAgents)
                .reply(reply.toString().trim())
                .pendingChoices(pendingChoices)
                .missingFields(missing.isEmpty() ? null : missing)
                .travelers(travelerNames(state))
                .travelerIds(travelerIdList(state))
                .destination(state.path("destination").asText(null))
                .origin(state.path("origin").asText(null))
                .draftJson(saved.getDraftJson())
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    @Override
    @Transactional
    public BizplayPlanAgentResponse createPlan(String sessionId, String corpNo, String bizplayToken,
                                               JsonNode approvalLines) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        // Per-corp settings (BizPlay endpoint / product code) resolve through this context.
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            return createPlanTurn(sessionId, corpNo, bizplayToken, approvalLines);
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    private BizplayPlanAgentResponse createPlanTurn(String sessionId, String corpNo, String bizplayToken,
                                                    JsonNode approvalLines) {
        BizplayPlanAgentRequest lookup = new BizplayPlanAgentRequest();
        lookup.setCorpNo(corpNo);
        lookup.setSessionId(sessionId);
        ConversationalAgentSession session = resolveSession(lookup);
        ArrayNode documents = documents(session);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("This session has no draft yet — choose a trip type first.");
        }
        ObjectNode state = loadState(session);
        List<String> missing = requiredGaps(documents.get(0), state);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Cannot create the plan — required fields are missing: "
                    + String.join(", ", missing) + ".");
        }
        applyPickedApprovalLines((ObjectNode) documents.get(0), approvalLines);

        // POST the draft_json AS-IS (it already has the save-body structure).
        String providerResponse = bizplayGatewayService.postPlanDraft(documents, bizplayToken);
        log.info("Plan draft saved to BizPlay: {}", providerResponse);

        session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
        String reply = t("ko".equals(state.path("lang").asText(null)),
                "All done — your trip plan has been saved to BizPlay.",
                "완료됐어요 — 출장 계획이 BizPlay에 저장되었습니다.");
        appendTurn(session, "assistant", reply);
        sessionRepo.save(session);
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent("CREATE_PLAN")
                .subAgents(List.of("BIZPLAY_GATEWAY"))
                .reply(reply)
                .travelers(travelerNames(state))
                .travelerIds(travelerIdList(state))
                .destination(state.path("destination").asText(null))
                .origin(state.path("origin").asText(null))
                .draftJson(saved.getDraftJson())
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    @Override
    public BizplayPlanAgentResponse createManualPlan(
            com.api.bizplay_conversational.model.request.BizplayManualPlanRequest request,
            String bizplayToken) {
        if (request.getCorpUserId() == null || request.getCorpUserId().isBlank()) {
            request.setCorpUserId(bizplayProperties.getDefaultCorpUserId());
        }
        // Per-corp settings (BizPlay endpoint / product code) resolve through this context.
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(request.getCorpNo());
        try {
            return createManualPlanTurn(request, bizplayToken);
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    private BizplayPlanAgentResponse createManualPlanTurn(
            com.api.bizplay_conversational.model.request.BizplayManualPlanRequest request,
            String bizplayToken) {
        // 1) Rebuild the save-ready skeleton from the RETRIEVED form — structure is never invented.
        JsonNode papers = bizplayGatewayService.getPapers(
                request.getPurposeId(), request.getSegmentId(), bizplayToken);
        BizplayFormResponse form = formSkeletonService.buildPlanSkeleton(
                papers, request.getPurposeId(), request.getSegmentId());
        ObjectNode document = ((ObjectNode) form.getDocument()).deepCopy();
        JsonNode fields = objectMapper.valueToTree(form.getFields());

        ObjectNode state = objectMapper.createObjectNode();
        state.set("fields", fields.deepCopy());
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            state.put("destination", request.getDestination().trim());
        }
        // travelerIds drive the per-traveler document fan-out; names are only presence markers here.
        ArrayNode travelerIds = state.withArray("travelerIds");
        ArrayNode travelerNames = state.withArray("travelers");
        if (request.getTravelerCorpUserIds() != null) {
            for (Long id : request.getTravelerCorpUserIds()) {
                if (id != null) {
                    travelerIds.add(id.longValue());
                    travelerNames.add(String.valueOf(id));
                }
            }
        }

        // 2) Values go through the SAME mapped-value writers the agent uses (verified encodings).
        ObjectNode mapped = objectMapper.createObjectNode();
        if (request.getTitle() != null) mapped.put("basic:BASIC_TITLE", request.getTitle());
        if (request.getContent() != null) mapped.put("basic:BASIC_CONTENT", request.getContent());
        for (JsonNode f : fields) {
            if ("BSTR_PERIOD".equals(f.path("type").asText())
                    && (request.getStartDate() != null || request.getEndDate() != null)) {
                ObjectNode period = mapped.putObject(f.path("key").asText());
                if (request.getStartDate() != null) period.put("start", request.getStartDate());
                if (request.getEndDate() != null) period.put("end", request.getEndDate());
            }
        }
        if (request.getItemValues() != null && request.getItemValues().isObject()) {
            request.getItemValues().fields().forEachRemaining(e -> mapped.set(e.getKey(), e.getValue()));
        }
        formValueWriterService.apply(document, fields, state, mapped);

        // 3) drafter + one document per traveler + picked approval lines
        setDraftUser(document, request.getCorpUserId());
        ArrayNode documents = objectMapper.createArrayNode();
        documents.add(document);
        syncTravelerDocuments(documents, state, request.getCorpUserId());
        applyPickedApprovalLines((ObjectNode) documents.get(0), request.getApprovalLines());

        List<String> missing = formValueWriterService.missingRequired(documents.get(0), fields, state);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Cannot create the plan — required fields are missing: "
                    + String.join(", ", missing) + ".");
        }

        String providerResponse = bizplayGatewayService.postPlanDraft(documents, bizplayToken);
        log.info("Manual plan draft saved to BizPlay: {}", providerResponse);
        String reply = "Plan draft saved to BizPlay.";
        syncSessionAfterManualSave(request.getAgentSessionId(), documents, reply);
        return BizplayPlanAgentResponse.builder()
                .sessionId(request.getAgentSessionId())
                .status(ConversationalAgentSession.AgentStatus.POSTED.name())
                .intent("CREATE_PLAN_MANUAL")
                .subAgents(List.of("FORM_BUILDER", "BIZPLAY_GATEWAY"))
                .reply(reply)
                .destination(state.path("destination").asText(null))
                .origin(state.path("origin").asText(null))
                .draftJson(documents)
                .build();
    }

    /**
     * WYSIWYG invariant: after a manual (form-driven) save that belongs to a chat session, the
     * session's draft_json is replaced with the documents that were ACTUALLY posted — the stored
     * draft and the created BizPlay document can never diverge again.
     */
    private void syncSessionAfterManualSave(String agentSessionId, JsonNode documents, String reply) {
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return;
        }
        try {
            sessionRepo.findById(UUID.fromString(agentSessionId.trim())).ifPresent(session -> {
                session.setDraftJson(documents);
                session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
                appendTurn(session, "assistant", reply);
                sessionRepo.save(session);
            });
        } catch (IllegalArgumentException e) {
            log.warn("Manual save: invalid agentSessionId '{}' — session not synced.", agentSessionId);
        }
    }

    // --- turn steps --------------------------------------------------------------

    /**
     * A side turn answered outside the form flow (custom agent, data query): the reply joins the
     * session history when one exists, and the draft is left untouched.
     */
    private BizplayPlanAgentResponse sideTurn(BizplayPlanAgentRequest request, String message,
                                              String reply, String intent, List<String> subAgents) {
        ConversationalAgentSession session = null;
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            try {
                session = sessionRepo.findById(UUID.fromString(request.getSessionId().trim())).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // Not a UUID — answer without touching any session.
            }
            if (session != null) {
                appendTurn(session, "user", message);
                appendTurn(session, "assistant", reply);
                sessionRepo.save(session);
            }
        }
        return BizplayPlanAgentResponse.builder()
                .sessionId(session != null ? session.getId().toString() : request.getSessionId())
                .status(session != null && session.getStatus() != null ? session.getStatus().name() : null)
                .intent(intent)
                .subAgents(subAgents)
                .reply(reply)
                .draftJson(session != null ? session.getDraftJson() : null)
                .build();
    }

    /**
     * DATA_QUERY turn: the guardrail classified the message as a read-only question about stored
     * data — answer it via the NL->SQL database-lookup sub-agent and leave the draft untouched.
     */
    private BizplayPlanAgentResponse dataQueryTurn(BizplayPlanAgentRequest request, String message, boolean ko) {
        DatabaseLookupAgentResponse lookup;
        try {
            lookup = databaseLookupAgentService.lookup(request.getCorpNo(), message);
        } catch (Exception e) {
            log.warn("Database lookup failed: {}", e.getMessage());
            lookup = null;
        }
        String reply = formatLookup(lookup, ko);

        // Ride the existing session when there is one so the Q&A stays in the conversation history.
        ConversationalAgentSession session = null;
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            try {
                session = sessionRepo.findById(UUID.fromString(request.getSessionId().trim())).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // Not a UUID — answer without touching any session.
            }
            if (session != null) {
                appendTurn(session, "user", message);
                appendTurn(session, "assistant", reply);
                sessionRepo.save(session);
            }
        }
        return BizplayPlanAgentResponse.builder()
                .sessionId(session != null ? session.getId().toString() : request.getSessionId())
                .status(session != null && session.getStatus() != null ? session.getStatus().name() : null)
                .intent("DATA_QUERY")
                .subAgents(List.of("GUARDRAIL_AGENT", "DATABASE_LOOKUP_AGENT"))
                .reply(reply)
                .draftJson(session != null ? session.getDraftJson() : null)
                .build();
    }

    /** Human sentence(s) out of an NL->SQL result — never a raw dump. */
    private String formatLookup(DatabaseLookupAgentResponse lookup, boolean ko) {
        if (lookup == null || !lookup.isExecuted() || lookup.getError() != null) {
            return t(ko,
                    "I couldn't look that up right now — sorry. Could you try asking another way?",
                    "지금은 조회하지 못했어요 — 죄송해요. 다른 방식으로 물어봐 주시겠어요?");
        }
        List<java.util.Map<String, Object>> rows = lookup.getRows() == null ? List.of() : lookup.getRows();
        if (rows.isEmpty()) {
            return t(ko, "I looked, but nothing matched that.", "조회해 봤지만 해당하는 결과가 없어요.");
        }
        // A single one-column row reads better as a sentence than as a labeled dump.
        if (rows.size() == 1 && rows.get(0).size() == 1) {
            Object only = rows.get(0).values().iterator().next();
            return t(ko,
                    "Here's what I found: " + (only == null ? "-" : only),
                    "조회 결과는 \"" + (only == null ? "-" : only) + "\"예요.");
        }
        StringBuilder sb = new StringBuilder(t(ko,
                "Here's what I found (" + rows.size() + " result" + (rows.size() == 1 ? "" : "s") + "):\n",
                "조회 결과예요 (" + rows.size() + "건):\n"));
        int shown = 0;
        for (java.util.Map<String, Object> row : rows) {
            if (shown++ == 10) {
                sb.append(t(ko, "…and " + (rows.size() - 10) + " more.", "…외 " + (rows.size() - 10) + "건."));
                break;
            }
            List<String> parts = new ArrayList<>();
            row.forEach((k, v) -> parts.add(k + ": " + (v == null ? "-" : v)));
            sb.append("• ").append(String.join(", ", parts)).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Spreadsheet / PDF sub-agents for uploaded files. Extractions come back as plain text facts
     * that ride the normal pipeline (field-mapper maps them; traveler names queue for the roster
     * resolver) — no separate merge logic to maintain.
     */
    private String extractFiles(BizplayPlanAgentRequest request, ObjectNode state,
                                List<String> subAgents, StringBuilder reply, boolean ko) {
        List<String> fileIds = request.getFileIds() == null ? List.of() : request.getFileIds();
        if (fileIds.isEmpty()) {
            return "";
        }
        StringBuilder facts = new StringBuilder();
        List<String> readFiles = new ArrayList<>();
        for (String fid : fileIds) {
            UploadedFile f = fileExtractionService.get(fid).orElse(null);
            if (f == null) {
                continue;
            }
            String fn = f.filename() == null ? fid : f.filename();
            String lower = fn.toLowerCase(java.util.Locale.ROOT);
            try {
                if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) {
                    if (!agentPromptService.isModuleEnabled("spreadsheet")) {
                        reply.append(t(ko,
                                "(The spreadsheet module is turned off — '" + fn + "' was skipped.) ",
                                "(스프레드시트 모듈이 꺼져 있어 '" + fn + "'을(를) 건너뛰었어요.) "));
                        continue;
                    }
                    SpreadsheetAnalysisResult sheet =
                            spreadsheetAgentService.analyze(request.getCorpNo(), f.content(), fn);
                    subAgents.add("SPREADSHEET_AGENT");
                    List<String> names = new ArrayList<>();
                    for (SpreadsheetAnalysisResult.ResolvedStaff s : sheet.getMatched()) {
                        if (s != null && s.isMatched() && s.getStaffName() != null) {
                            names.add(s.getStaffName());
                            addPendingTraveler(state, s.getStaffName());
                        }
                    }
                    if (!names.isEmpty()) {
                        facts.append("Travelers listed in the uploaded spreadsheet '").append(fn)
                                .append("': ").append(String.join(", ", names)).append(". ");
                    }
                    if (!sheet.getUnmatched().isEmpty()) {
                        reply.append(t(ko,
                                "From " + fn + ", I couldn't match: " + String.join(", ", sheet.getUnmatched()) + ". ",
                                fn + "에서 " + String.join(", ", sheet.getUnmatched()) + "은(는) 명단에서 찾지 못했어요. "));
                    }
                    readFiles.add(fn);
                } else if (lower.endsWith(".pdf")) {
                    if (!agentPromptService.isModuleEnabled("pdf")) {
                        reply.append(t(ko,
                                "(The PDF module is turned off — '" + fn + "' was skipped.) ",
                                "(PDF 모듈이 꺼져 있어 '" + fn + "'을(를) 건너뛰었어요.) "));
                        continue;
                    }
                    TextAnalysisResult pdf =
                            pdfAgentService.analyze(request.getCorpNo(), f.content(), fn, List.of());
                    subAgents.add("PDF_AGENT");
                    StringBuilder p = new StringBuilder();
                    appendFact(p, "destination", pdf.getTripDestination());
                    appendFact(p, "start date", pdf.getBusinessStartDate());
                    appendFact(p, "end date", pdf.getBusinessEndDate());
                    appendFact(p, "title", pdf.getTitle());
                    appendFact(p, "purpose/content", pdf.getContent() != null ? pdf.getContent() : pdf.getPurpose());
                    if (pdf.getTravelers() != null) {
                        List<String> names = new ArrayList<>();
                        for (TextAnalysisResult.TravelerRoute route : pdf.getTravelers()) {
                            if (route != null && route.getName() != null && !route.getName().isBlank()) {
                                names.add(route.getName());
                                addPendingTraveler(state, route.getName());
                            }
                        }
                        if (!names.isEmpty()) {
                            appendFact(p, "travelers", String.join(", ", names));
                        }
                    }
                    if (p.length() > 0) {
                        facts.append("From the uploaded document '").append(fn).append("': ")
                                .append(p).append(". ");
                    }
                    readFiles.add(fn);
                } else {
                    reply.append(t(ko,
                            "(I can't read '" + fn + "' — only Excel/CSV and PDF files are supported.) ",
                            "('" + fn + "' 형식은 읽을 수 없어요 — 엑셀/CSV과 PDF만 지원해요.) "));
                }
            } catch (Exception e) {
                log.warn("File extraction failed for '{}': {}", fn, e.getMessage());
                reply.append(t(ko,
                        "(I couldn't read '" + fn + "'.) ",
                        "('" + fn + "'을(를) 읽지 못했어요.) "));
            }
        }
        if (!readFiles.isEmpty()) {
            reply.append(t(ko,
                    "I've read " + String.join(", ", readFiles) + ". ",
                    String.join(", ", readFiles) + " 파일을 읽었어요. "));
        }
        return facts.toString().trim();
    }

    private static void appendFact(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(label).append(" = ").append(value.trim());
    }

    /** Queue a traveler NAME for the roster resolver (skips names already present/resolved). */
    private void addPendingTraveler(ObjectNode state, String name) {
        ArrayNode arr = state.withArray("travelers");
        for (JsonNode n : arr) {
            if (n.asText("").equalsIgnoreCase(name)) {
                return;
            }
        }
        arr.add(name);
    }

    /**
     * Conversation language for this turn. The web chat prepends an explicit instruction to every
     * message ("Respond in Korean only…" / "Respond in English only…"); without one, fall back to
     * Hangul detection on the message itself.
     */
    private boolean koreanConversation(String message) {
        if (message.contains("Respond in Korean only")) {
            return true;
        }
        if (message.contains("Respond in English only")) {
            return false;
        }
        // Proportion, not presence: an English sentence quoting a Korean name (김도하)
        // must still count as English.
        long hangul = message.codePoints().filter(cp -> cp >= 0xAC00 && cp <= 0xD7A3).count();
        long latin = message.codePoints().filter(Character::isAlphabetic).count() - hangul;
        return hangul > 0 && hangul * 3 > latin;
    }

    /** Reply fragment in the conversation's language — never mix the two in one turn. */
    private static String t(boolean ko, String en, String kr) {
        return ko ? kr : en;
    }

    /**
     * Required-field gaps for the draft. The retrieved BizPlay spec leaves the trip period
     * optional, but a plan without dates makes no sense — treat the period as required so the
     * agent keeps asking (and the save flow refuses) until the dates are set.
     */
    private List<String> requiredGaps(JsonNode document, ObjectNode state) {
        List<String> missing = new ArrayList<>(
                formValueWriterService.missingRequired(document, state.path("fields"), state));
        if (!document.hasNonNull("bstrStartDate")) {
            String label = "출장 기간";
            for (JsonNode f : state.path("fields")) {
                if ("BSTR_PERIOD".equals(f.path("type").asText())) {
                    label = f.path("label").asText(label);
                    break;
                }
            }
            if (!missing.contains(label)) {
                missing.add(label);
            }
        }
        return missing;
    }

    /** Sub-agent [C]: LLM field mapping + deterministic writes into the request-body document.
     *  Returns a clarifying question when the user's input was INDEFINITE ("sometime next
     *  week") — the caller asks it instead of pretending the turn changed something. */
    private String fillFields(ObjectNode document, ObjectNode state, String text,
                            List<String> subAgents, StringBuilder reply, boolean ko) {
        JsonNode mapped = fieldMapperAgentService.mapFields(text, state.path("fields"));
        subAgents.add("FIELD_MAPPER_AGENT");
        List<String> applied = new ArrayList<>(
                formValueWriterService.apply(document, state.path("fields"), state, mapped));
        applied.addAll(ensurePeriodFallback(document, state, text));
        applied.addAll(bindPendingAnswer(document, state, text, mapped));
        state.remove("staged"); // consumed
        if (!applied.isEmpty()) {
            // The UI shows every captured value in the plan summary — the reply just talks.
            reply.append(t(ko,
                    "I've filled in the details you gave me. ",
                    "말씀해 주신 내용을 계획에 반영했어요. "));
        }
        String clarify = mapped.path("clarify").asText(null);
        if (clarify != null && !clarify.isBlank()) {
            // Language guard: a clarify in the wrong language falls back to a neutral ask.
            boolean hasHangul = clarify.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
            if (hasHangul != ko) {
                return t(ko, "Sure — could you give me the exact value (e.g. the specific dates)?",
                        "네 — 정확한 값(예: 구체적인 날짜)을 알려주시겠어요?");
            }
            return clarify.trim();
        }
        return null;
    }

    /** Focused re-reads per turn — bounds the cost when a form has many required fields. */
    private static final int MAX_FIELD_RETRIES = 4;

    /**
     * Last chance before asking: re-read THIS turn's text once per still-missing field. Mapping a
     * whole form in one LLM call misses values that are plainly in the sentence ("a trip to Busan"
     * leaving 출장지 empty), and asking the user for what they just said reads broken. A focused
     * "what is <field>?" call is far more reliable. Travelers are excluded — names go through the
     * roster resolver, never a free-text guess — and the period has its own ISO-date fallback.
     */
    private List<String> retryMissingField(ObjectNode document, ObjectNode state, String text,
                                           List<String> missing, List<String> subAgents) {
        if (missing.isEmpty() || text == null || text.isBlank()) {
            return missing;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        int tried = 0;
        for (String label : missing) {
            if (tried >= MAX_FIELD_RETRIES) {
                break;
            }
            JsonNode field = fieldByLabel(state, label);
            String type = field == null ? "" : field.path("type").asText("");
            if (field == null || "BASIC_TRAVELER".equals(type) || "BSTR_PERIOD".equals(type)) {
                continue;
            }
            tried++;
            String value = fieldMapperAgentService.extractField(text, field);
            if (value != null) {
                payload.set(field.path("key").asText(), shapeValue(field, value));
            }
        }
        if (payload.isEmpty()) {
            return missing;
        }
        subAgents.add("FIELD_MAPPER_AGENT");
        formValueWriterService.apply(document, state.path("fields"), state, payload);
        return requiredGaps(document, state);
    }

    private JsonNode fieldByLabel(ObjectNode state, String label) {
        for (JsonNode f : state.path("fields")) {
            if (label.equals(f.path("label").asText())) {
                return f;
            }
        }
        return null;
    }

    /** A bare value in the shape this field's writer expects. */
    private JsonNode shapeValue(JsonNode field, String answer) {
        if ("BASIC_TRAVELER".equals(field.path("type").asText())) {
            ObjectNode names = objectMapper.createObjectNode();
            names.putArray("names").add(answer);
            return names;
        }
        if (field.path("options").size() > 0) {
            return objectMapper.createObjectNode().put("choice", answer);
        }
        return objectMapper.getNodeFactory().textNode(answer);
    }

    /** Self-reference answers to "who is travelling?" — the speaker means themselves. */
    private static final java.util.regex.Pattern SELF_REFERENCE = java.util.regex.Pattern.compile(
            "(?iu)^\\W*(it'?s\\s+me|it\\s+is\\s+me|i\\s+am|i'?m|me|myself|i|just\\s+me|only\\s+me|"
            + "나|저|본인|나만|저만|제가|내가|나요|저요)\\W*$");

    /**
     * Rewrite self-referring traveller answers ("It is me.", "본인") to the requesting user's roster
     * name, in both the pending list and the stored state — otherwise the resolver searches the
     * staff directory for the phrase itself and reports it as an unknown person.
     */
    private void replaceSelfReference(ObjectNode state, List<String> pending, JsonNode roster, String corpUserId) {
        long me = parseLongOr(corpUserId == null ? "" : corpUserId, -1);
        if (me <= 0) {
            return;
        }
        String myName = null;
        for (JsonNode u : roster.path("users").isArray() ? roster.path("users") : roster) {
            long id = u.path("corporationUserId").asLong(u.path("id").asLong(-1));
            if (id == me) {
                myName = u.path("userName").asText(u.path("name").asText(null));
                break;
            }
        }
        if (myName == null || myName.isBlank()) {
            return;
        }
        for (int i = 0; i < pending.size(); i++) {
            if (SELF_REFERENCE.matcher(pending.get(i)).matches()) {
                log.info("Traveller '{}' means the requesting user — resolved to {}.", pending.get(i), myName);
                renameTraveler(state, pending.get(i), myName);
                pending.set(i, myName);
            }
        }
    }

    /** Swap a held traveller name in agent state (keeps state and the pending list in step). */
    private void renameTraveler(ObjectNode state, String from, String to) {
        ArrayNode held = state.withArray("travelers");
        for (int i = 0; i < held.size(); i++) {
            if (from.equals(held.get(i).asText())) {
                held.set(i, objectMapper.getNodeFactory().textNode(to));
            }
        }
    }

    /** Store the field key behind the question we are about to ask (matched by its label). */
    private void rememberPendingAsk(ObjectNode state, String askedLabel) {
        for (JsonNode f : state.path("fields")) {
            if (askedLabel.equals(f.path("label").asText())) {
                state.put("pendingAsk", f.path("key").asText());
                return;
            }
        }
        state.remove("pendingAsk");   // synthesized label (e.g. the forced period) — nothing to bind
    }

    /**
     * The agent asks for exactly ONE required field per turn, so the next message is almost always
     * the bare answer to it ("고객사 정기 미팅 진행합니다." to "내용을 알려 주세요"). Such an answer names
     * no field, so the mapper LLM cannot place it — bind it deterministically to the field we just
     * asked about, unless the mapper already filled that field or the user clearly changed subject
     * (a question, a chip token, or a message the mapper mapped elsewhere).
     */
    private List<String> bindPendingAnswer(ObjectNode document, ObjectNode state, String text,
                                           JsonNode mapped) {
        String pendingKey = state.path("pendingAsk").asText(null);
        if (pendingKey == null || pendingKey.isBlank() || text == null || text.isBlank()) {
            return List.of();
        }
        state.remove("pendingAsk");   // consumed either way — never ask-bind twice
        String answer = stripChatContext(text).trim();
        if (answer.isEmpty() || answer.endsWith("?") || answer.toLowerCase().startsWith("trip type:")
                || (mapped != null && mapped.has(pendingKey))) {
            return List.of();   // already placed, or not an answer at all
        }
        JsonNode field = null;
        for (JsonNode f : state.path("fields")) {
            if (pendingKey.equals(f.path("key").asText())) {
                field = f;
                break;
            }
        }
        if (field == null || isFilledField(document, state, field)) {
            return List.of();
        }
        String type = field.path("type").asText("");
        // The period needs real dates — ensurePeriodFallback already handled any it could parse.
        if ("BSTR_PERIOD".equals(type)) {
            return List.of();
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set(pendingKey, shapeValue(field, answer));
        log.info("Bound bare answer to the pending question: {} = {}", pendingKey, answer);
        return formValueWriterService.apply(document, state.path("fields"), state, payload);
    }

    /** True when this one field already holds a value (asked as required, per the writer's rules). */
    private boolean isFilledField(ObjectNode document, ObjectNode state, JsonNode field) {
        ObjectNode asRequired = field.deepCopy();
        asRequired.put("required", true);
        ArrayNode one = objectMapper.createArrayNode();
        one.add(asRequired);
        return formValueWriterService.missingRequired(document, one, state).isEmpty();
    }

    private static final java.util.regex.Pattern ISO_DATE =
            java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * Deterministic safety net: explicit ISO dates in the message must never be lost to LLM
     * nondeterminism. If the mapper did not fill the trip period but the text contains dates,
     * write first..last through the SAME period writer path.
     */
    private List<String> ensurePeriodFallback(ObjectNode document, ObjectNode state, String text) {
        if (document.hasNonNull("bstrStartDate") || text == null) {
            return List.of();
        }
        List<String> dates = new ArrayList<>();
        java.util.regex.Matcher m = ISO_DATE.matcher(text);
        while (m.find()) {
            dates.add(m.group());
        }
        if (dates.isEmpty()) {
            return List.of();
        }
        String periodKey = null;
        for (JsonNode f : state.path("fields")) {
            if ("BSTR_PERIOD".equals(f.path("type").asText())) {
                periodKey = f.path("key").asText();
                break;
            }
        }
        if (periodKey == null) {
            return List.of();
        }
        ObjectNode value = objectMapper.createObjectNode();
        value.put("start", dates.get(0));
        value.put("end", dates.get(dates.size() - 1));
        ObjectNode mapped = objectMapper.createObjectNode();
        mapped.set(periodKey, value);
        return formValueWriterService.apply(document, state.path("fields"), state, mapped);
    }

    private String stagedPlus(ObjectNode state, String message) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode staged : state.path("staged")) {
            String s = staged.asText("");
            // Chip clicks carry no trip details; skip them when replaying context.
            if (!s.isBlank() && !s.toLowerCase().startsWith("trip type:")) {
                sb.append(s).append('\n');
            }
        }
        if (!message.toLowerCase().startsWith("trip type:") || sb.isEmpty()) {
            sb.append(message);
        }
        return sb.toString();
    }

    /** Chip token for step one: the Travel Purpose on its own. */
    private static final java.util.regex.Pattern PURPOSE_PICK =
            // The chat pastes a form-state preamble in front of every message, so the token has to
            // be found ANYWHERE in the text — an anchored match never fired from the UI at all.
            java.util.regex.Pattern.compile("(?i).*purpose:(\\d+).*", java.util.regex.Pattern.DOTALL);

    /**
     * Step one: one chip per Travel Purpose. Candidates come from the resolver (it may already have
     * narrowed things), but the segments are looked up across the WHOLE catalog so a purpose whose
     * trip types were filtered out still leads somewhere.
     */
    private TripPlanAgentResponse.PendingChoice purposeChoice(List<PurposeOption> candidates,
                                                             List<PurposeOption> all) {
        java.util.LinkedHashMap<Long, String> byPurpose = new java.util.LinkedHashMap<>();
        for (PurposeOption c : candidates) {
            byPurpose.putIfAbsent(c.getPurposeId(), c.getPurposeName());
        }
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (java.util.Map.Entry<Long, String> e : byPurpose.entrySet()) {
            long id = e.getKey();
            int segments = 0;
            for (PurposeOption o : all) {
                if (o.getPurposeId() == id && o.getSegmentId() != null) {
                    segments++;
                }
            }
            java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
            meta.put("segments", String.valueOf(segments));
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(e.getValue())
                    .sendText("purpose:" + id)
                    .meta(meta)
                    .build());
        }
        return TripPlanAgentResponse.PendingChoice.builder()
                .kind("PURPOSE").name("travel purpose").options(options).build();
    }

    /** Step two: the trip types of the purpose just picked. */
    private TripPlanAgentResponse.PendingChoice segmentChoice(List<PurposeOption> ofPurpose) {
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (PurposeOption c : ofPurpose) {
            String label = c.getSegmentName() == null || c.getSegmentName().isBlank()
                    ? c.getPurposeName() : c.getSegmentName();
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(label)
                    .sendText(c.getSendText())
                    .build());
        }
        return TripPlanAgentResponse.PendingChoice.builder()
                .kind("SEGMENT").name("trip type").options(options).build();
    }

    /** RETIRED (kept for reference, do not delete): the flat "purpose · segment" chip row. */
    private TripPlanAgentResponse.PendingChoice toPendingChoice(List<PurposeOption> candidates) {
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (PurposeOption c : candidates) {
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(c.getLabel())
                    .sendText(c.getSendText())
                    .build());
        }
        return TripPlanAgentResponse.PendingChoice.builder()
                .kind("PURPOSE")
                .name("trip type")
                .options(options)
                .build();
    }

    /** Chip sendText of a traveler disambiguation pick: "Traveler: <userName> (<id>) for <input>". */
    private static final java.util.regex.Pattern TRAVELER_PICK =
            java.util.regex.Pattern.compile("^Traveler:\\s*(.+?)\\s*\\((\\d+)\\)\\s*for\\s*(.+)$");

    /**
     * Resolve traveler names that are not yet mapped to a corporationUserId, using the roster tool.
     * Unique match -> id recorded; several matches (duplicates / a department) -> chips for the user;
     * none -> reported in the reply. Runs every turn, so names stuck pending resolve as soon as the
     * roster (or the input) allows.
     */
    private List<TripPlanAgentResponse.PendingChoice> resolveTravelers(BizplayPlanAgentRequest request,
                                                                       ObjectNode state, String token,
                                                                       StringBuilder reply, List<String> subAgents,
                                                                       boolean ko) {
        List<String> pending = new ArrayList<>();
        for (JsonNode n : state.path("travelers")) {
            String name = n.asText("");
            if (!name.isBlank() && !state.path("resolvedTravelers").has(name)) {
                pending.add(name);
            }
        }
        if (pending.isEmpty()) {
            return List.of();
        }
        Long corporationId = request.getCorporationId() != null
                ? request.getCorporationId()
                : corporationIdFromToken(token);
        if (corporationId == null) {
            reply.append(t(ko,
                    "(I couldn't reach the staff directory just now, so traveller names are unverified.) ",
                    "(직원 명부를 조회할 수 없어 출장자 이름을 확인하지 못했어요.) "));
            return List.of();
        }
        JsonNode roster = bizplayGatewayService.getCorporationUsers(corporationId, token);
        // "me"/"본인" is not a name to look up — it is the requesting user, whose id we already
        // have. Swap it for their roster name BEFORE matching, or the search fails on the phrase.
        replaceSelfReference(state, pending, roster, request.getCorpUserId());
        List<TravelerResolverService.Resolution> resolutions = travelerResolverService.resolve(pending, roster);
        subAgents.add("TRAVELER_RESOLVER");

        List<TripPlanAgentResponse.PendingChoice> chips = new ArrayList<>();
        List<String> notFound = new ArrayList<>();   // batched into ONE sentence, not one each
        for (TravelerResolverService.Resolution r : resolutions) {
            if (r.matched() != null) {
                // Keep the ROSTER name in state (a romanized/partial input like "Kim Doha" would
                // never match the roster again downstream — e.g. in the manual WYSIWYG save).
                applyTravelerPick(state, r.matched().userName(), r.matched().corporationUserId(),
                        r.input(), new StringBuilder(), ko);
                if (!r.input().equals(r.matched().userName())) {
                    reply.append(t(ko,
                            "'" + r.input() + "' looks like " + r.matched().userName()
                                    + " — I've added them to the trip. ",
                            "'" + r.input() + "'은(는) " + r.matched().userName()
                                    + " 님으로 확인해서 출장자에 추가했어요. "));
                }
            } else if (r.candidates() != null && !r.candidates().isEmpty()) {
                List<TripPlanAgentResponse.Option> options = new ArrayList<>();
                for (TravelerResolverService.Candidate c : r.candidates()) {
                    String dept = c.departmentName() == null ? "?" : c.departmentName();
                    String pos = c.positionName() == null ? "?" : c.positionName();
                    options.add(TripPlanAgentResponse.Option.builder()
                            .label(c.userName() + " · " + dept + " · " + pos)
                            .sendText("Traveler: " + c.userName() + " (" + c.corporationUserId() + ") for " + r.input())
                            .build());
                }
                chips.add(TripPlanAgentResponse.PendingChoice.builder()
                        .kind("TRAVELER").name(r.input()).options(options).build());
                reply.append(t(ko,
                        "A few people match '" + r.input() + "' — could you pick the right one below? ",
                        "'" + r.input() + "'에 해당하는 분이 여러 명이에요 — 아래에서 맞는 분을 골라 주시겠어요? "));
            } else {
                notFound.add(r.input());
            }
        }
        // Drop what could not be resolved. Leaving it in state made the SAME warning repeat on
        // every later turn - the name is re-queued each time - and a real name given afterwards
        // was APPENDED beside the bad one instead of replacing it, so the traveller list grew
        // ['인천에서 출발해', '김충북']. A name nobody recognises is not a traveller; say so once
        // and forget it, which also leaves the text free to be read as what it really was.
        for (String bad : notFound) {
            ArrayNode held = state.withArray("travelers");
            for (int i = held.size() - 1; i >= 0; i--) {
                if (bad.equals(held.get(i).asText(""))) {
                    held.remove(i);
                }
            }
            log.info("Traveler '{}' did not match the roster - dropped rather than kept pending.", bad);
        }
        if (notFound.size() == 1) {
            reply.append(t(ko,
                    "I couldn't find '" + notFound.get(0) + "' in the staff list — could you double-check the name? ",
                    "'" + notFound.get(0) + "' 님을 직원 명단에서 찾지 못했어요 — 이름을 다시 확인해 주시겠어요? "));
        } else if (!notFound.isEmpty()) {
            String joined = String.join(", ", notFound);
            reply.append(t(ko,
                    "I couldn't find these people in the staff list: " + joined + " — could you check the names? ",
                    "다음 분들을 직원 명단에서 찾지 못했어요: " + joined + " — 이름을 확인해 주시겠어요? "));
        }
        return chips;
    }

    /** Apply a traveler chip pick: swap the queried input for the chosen person and record the id. */
    private void applyTravelerPick(ObjectNode state, String userName, long id, String input,
                                   StringBuilder reply, boolean ko) {
        ArrayNode travelers = state.withArray("travelers");
        for (int i = travelers.size() - 1; i >= 0; i--) {
            if (travelers.get(i).asText("").equalsIgnoreCase(input)) {
                travelers.remove(i);
            }
        }
        boolean exists = false;
        for (JsonNode n : travelers) {
            if (n.asText("").equalsIgnoreCase(userName)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            travelers.add(userName);
        }
        ObjectNode resolved = state.withObject("resolvedTravelers");
        resolved.remove(input);
        resolved.put(userName, id);
        addTravelerId(state, id);
        reply.append(t(ko,
                "Done — " + userName + " is on the traveller list. ",
                "네 — " + userName + " 님을 출장자 명단에 추가했어요. "));
    }

    private void addTravelerId(ObjectNode state, long id) {
        ArrayNode ids = state.withArray("travelerIds");
        for (JsonNode n : ids) {
            if (n.asLong() == id) {
                return;
            }
        }
        ids.add(id);
    }

    /** "인천에서 출발", "출발지는 인천", "from Incheon" - the ways an origin gets stated on its own. */
    private static final java.util.regex.Pattern ORIGIN_PHRASE = java.util.regex.Pattern.compile(
            "(?i)(?:출발지(?:는|은|가|이)?\\s*|from\\s+)([\\p{L}\\p{N}]{2,20})|([\\p{L}\\p{N}]{2,20})에서\\s*출발");

    /**
     * Read an origin the user stated on its own and write it to state. Only fills a blank
     * origin, or replaces one when the message names it explicitly - a place mentioned in
     * passing must not overwrite what the form already holds.
     */
    private void backfillOrigin(ObjectNode state, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        java.util.regex.Matcher m = ORIGIN_PHRASE.matcher(message);
        if (!m.find()) {
            return;
        }
        String place = m.group(1) != null ? m.group(1) : m.group(2);
        if (place == null || place.isBlank()) {
            return;
        }
        String current = state.path("origin").asText("");
        if (place.equals(current)) {
            return;
        }
        state.put("origin", place);
        log.info("Origin '{}' read from the message after the form mapper left it empty.", place);
    }

    /**
     * Korea-only place validation ("only apply in Korea"): runs when the chosen purpose is
     * DOMESTIC (name contains 국내) and a place changed since its last check. Covers BOTH the
     * destination (출장지) and the origin (출발지) when the user gave one. VALID results are
     * remembered silently; UNKNOWN appends a one-time, non-blocking warning per place.
     */
    private void validateDestinationIfKorean(ObjectNode state, List<String> subAgents, StringBuilder reply,
                                             boolean ko) {
        String purposeName = state.path("purpose").path("purposeName").asText("");
        if (!purposeName.contains("국내")) {
            return;   // overseas / corp-specific purposes: not Korean places, skip
        }
        validateKoreanPlace(state, subAgents, reply, ko,
                "destination", "validatedDestination", "destination (출장지)", "출장지");
        validateKoreanPlace(state, subAgents, reply, ko,
                "origin", "validatedOrigin", "departure place (출발지)", "출발지");
    }

    private void validateKoreanPlace(ObjectNode state, List<String> subAgents, StringBuilder reply,
                                     boolean ko, String stateKey, String cacheKey,
                                     String labelEn, String labelKo) {
        String place = state.path(stateKey).asText(null);
        if (place == null || place.isBlank()) {
            return;
        }
        if (place.equals(state.path(cacheKey).asText(null))) {
            return;   // unchanged since the last check — don't re-validate or re-warn
        }
        PlaceValidationService.Result result = placeValidationService.validateKorean(place);
        if (!subAgents.contains("PLACE_VALIDATOR")) {
            subAgents.add("PLACE_VALIDATOR");
        }
        state.put(cacheKey, place);
        if (result.status() == PlaceValidationService.Result.Status.UNKNOWN) {
            reply.append(t(ko,
                    "One thing to double-check: I couldn't find '" + place + "' among Korean regions for the "
                            + labelEn + " — could you confirm the place name? ",
                    "한 가지 확인해 주세요: " + labelKo + " '" + place
                            + "'을(를) 국내 지역에서 찾지 못했어요 — 지역명을 확인해 주시겠어요? "));
        }
    }

    /** The web chat prepends "(Current user: … Respond in X only, no Y.) " — return just the
     *  user's text. Anchored on the prefix's fixed language sentence, since form-state values
     *  inside the prefix may themselves contain parentheses. */
    private static String stripChatContext(String m) {
        if (m != null && m.startsWith("(Current user:")) {
            return m.replaceFirst("(?s)^\\(Current user:.*?no (Korean|English)\\.\\)\\s*", "").trim();
        }
        return m;
    }

    /**
     * A question or view request about the current draft — not new information for the form.
     * Broad on purpose: the draft-QA LLM decides WHAT to show; we only decide it's a question.
     * Messages that also carry edits ("add 김철수 and show me the result") stay with the mapper.
     */
    private boolean isDraftQuestion(String m) {
        if (m == null || m.isBlank() || m.length() > 100) {
            return false;
        }
        boolean edits = m.matches("(?ius).*(add|change|set|remove|update|replace|make|추가|변경|수정|바꿔|빼|넣|삭제).*");
        if (edits) {
            return false;
        }
        boolean viewVerb = m.matches("(?ius).*(show|preview|view|display|summar|list|detail|status|so far|"
                + "보여|알려|요약|정리|현황|상태|지금까지).*");
        boolean question = m.contains("?")
                && m.matches("(?ius).*(what|which|who|when|where|how|is|are|did|do|"
                + "뭐|무엇|누구|언제|어디|몇|인가|나요|까요|어때).*");
        return viewVerb || question;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

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
            log.warn("Could not decode corporationId from token: {}", e.getMessage());
            return null;
        }
    }


    /** Keys the captured save body carries ONLY on the drafter's (first) document. */
    private static final String[] DRAFTER_ONLY_KEYS = {"bstrStatusType", "externalLinks", "totalBstrAmount"};

    /**
     * One document per row of the BizPlay save body. documents[0] is ALWAYS the drafter (the
     * requesting user), carrying the full key set and the approval lines; every other row is a
     * clone without the drafter-only keys, with its own draftUserId and empty approval lines.
     *
     * Whether the drafter's row is a TRAVELER or a drafting-only marker is what bstrStatusType
     * encodes (verified against real UI-created plans read back from the provider):
     *   drafter travels     -> their row IS the traveler row and carries NO bstrStatusType;
     *                          companions come back as ALONG.
     *   drafter stays home  -> their row is an extra DRAFT_ONLY row holding the common values,
     *                          and every actual traveler follows it.
     * So a DRAFT_ONLY row present == the drafter is not on this trip — true even when drafting
     * on someone else's behalf.
     */
    private void syncTravelerDocuments(ArrayNode documents, JsonNode state, String corpUserId) {
        if (documents.isEmpty()) {
            return;
        }
        ObjectNode master = (ObjectNode) documents.get(0);
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : state.path("travelerIds")) {
            if (n.canConvertToLong() && !ids.contains(n.asLong())) {
                ids.add(n.asLong());
            }
        }
        if (ids.isEmpty()) {
            return; // names not yet resolved -> single-document draft
        }
        long drafter = parseLongOr(corpUserId, ids.get(0));
        boolean drafterTravels = ids.remove(drafter);
        if (drafterTravels) {
            // Row 0 IS the drafter's own traveler row — no drafting-only marker.
            master.remove("bstrStatusType");
        } else {
            master.put("bstrStatusType", "DRAFT_ONLY");
        }
        master.put("draftUserId", drafter);
        while (documents.size() > 1) {
            documents.remove(documents.size() - 1);
        }
        ensureDraftApprovalLine(master);
        // Remaining ids are the accompanying travelers; each clones the master's common values.
        for (Long id : ids) {
            ObjectNode follower = master.deepCopy();
            for (String key : DRAFTER_ONLY_KEYS) {
                follower.remove(key);
            }
            follower.put("draftUserId", id.longValue());
            // The capture shows approval lines ONLY on the drafter's document; followers are empty.
            follower.set("approvalLines", objectMapper.createArrayNode());
            documents.add(follower);
        }
    }

    private long parseLongOr(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void setDraftUser(ObjectNode document, String corpUserId) {
        try {
            document.put("draftUserId", Long.parseLong(corpUserId.trim()));
        } catch (NumberFormatException e) {
            document.put("draftUserId", corpUserId.trim());
        }
        ensureDraftApprovalLine(document);
    }

    /**
     * "Set approval order" picks from the UI: rebuild the master document's approvalLines as the
     * drafter's DRAFT line followed by the picked lines in order. Line shape mirrors the DRAFT
     * line the working capture carries (EMPLOYEE, no department); only the ids and kinds come
     * from the user's picks — nothing is invented.
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

    /**
     * The working capture always carries a DRAFT approval line for the drafting user (approvers
     * beyond it are hand-picked in the BizPlay UI — paperApprovalLineSettingDto is null, so they
     * cannot be derived from the form). Built purely from the drafter id — no invented data.
     */
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

    private List<String> travelerNames(JsonNode state) {
        List<String> names = new ArrayList<>();
        for (JsonNode n : state.path("travelers")) {
            names.add(n.asText());
        }
        return names.isEmpty() ? null : names;
    }

    private List<Long> travelerIdList(JsonNode state) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : state.path("travelerIds")) {
            if (n.canConvertToLong()) {
                ids.add(n.asLong());
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    // --- session + state ---------------------------------------------------------

    private ConversationalAgentSession resolveSession(BizplayPlanAgentRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            ConversationalAgentSession created = new ConversationalAgentSession();
            created.setCorpNo(request.getCorpNo());
            created.setAgentType(ConversationalAgentSession.AgentType.TRIP_PLAN);
            // NOTE: leave id null — sessionRepo.save() only INSERTs when the id is unset.
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

    /** draft_json IS the request-body array (empty until the trip type is chosen). */
    private ArrayNode documents(ConversationalAgentSession session) {
        JsonNode draft = session.getDraftJson();
        return (draft instanceof ArrayNode a) ? a : objectMapper.createArrayNode();
    }

    /** Agent bookkeeping rides in chat_event_json under a dedicated non-chat role. */
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
                        log.warn("Could not parse agent state; starting fresh: {}", ex.getMessage());
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
}
