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
    /** How many past turns sub-agents may see. Two exchanges. */
    // 16 entries = 8 user/assistant exchanges. Longer context lets judges resolve
    // references further back; the echo-not-edit guards keep stale history values
    // from being re-applied to the draft.
    private static final int RECENT_TURNS = 16;

    private final ConversationalAgentSessionRepo sessionRepo;
    private final GuardrailAgentService guardrailAgentService;
    private final com.api.bizplay_conversational.service.slotFillerAgentService.SlotFillerAgentService slotFillerAgentService;
    private final com.api.bizplay_conversational.service.planEnrichmentService.PlanEnrichmentService planEnrichmentService;
    private final com.api.bizplay_conversational.service.destinationResolverAgentService.DestinationResolverAgentService destinationResolverAgentService;
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
    private final com.api.bizplay_conversational.service.turnVerifierAgentService.TurnVerifierAgentService turnVerifierAgentService;
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
            return chatTurn(request, bizplayToken, false);
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    /**
     * One turn. {@code retryOfMisalignedTurn} is true on the SECOND attempt at the same message —
     * the alignment gate rolled the draft back and is trying once more with what the verifier
     * said was missed. A retry is never itself verified: one correction attempt, then answer.
     */
    private BizplayPlanAgentResponse chatTurn(BizplayPlanAgentRequest request, String bizplayToken,
                                              boolean retryOfMisalignedTurn) {
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
        // Judge the USER's words, not the web client's "(Current user: ...)" preamble — the
        // prefix made every short answer look like a long request and defeated this guard.
        String routeText = stripChatContext(message);
        boolean shortAnswer = !routeText.contains("?")
                && (routeText.length() < 20 || routeText.split("\\s+").length <= 3);
        // While THIS agent has a question pending (destination, transport, 상세, a form
        // field), the next message is its answer — a custom agent must never claim it
        // ("오사카 본사 3층이야" went to an address-checker instead of the detail slot).
        boolean askPending = false;
        if (midSession) {
            try {
                ObjectNode st = loadState(resolveSession(request));
                askPending = st.has("pendingAsk")
                        || st.path("pendingDestinationAsk").asBoolean(false)
                        || st.path("pendingTransportAsk").asBoolean(false)
                        || st.path("pendingOriginAsk").asBoolean(false)
                        || st.path("pendingDetailAsk").asBoolean(false)
                        || st.path("pendingRouteAsk").asBoolean(false);
            } catch (Exception ignored) {
                // unknown session — the normal flow will surface that error itself
            }
        }
        // A question about THIS conversation's draft outranks every custom agent — a
        // 결재-themed agent was claiming "결재선에 누구 넣었지?" away from the draft Q&A.
        // Draft Q&A: on an ACTIVE session, questions about the plan ("show me all the details",
        // "who are the travelers?", "언제 가는 걸로 돼 있어?") answer from the DRAFT itself —
        // any subset, any phrasing. Must run BEFORE the DATA_QUERY route, which would otherwise
        // dump unrelated database rows for the same questions.
        String bareMessage = stripChatContext(message);
        // While an ask is pending, only an EXPLICIT question ("?") may divert to Q&A — the
        // answer to the ask often shares surface words with the view-verb heuristic ("no
        // specific destination detail" is a decline, not a request to see details).
        if (midSession && isDraftQuestion(bareMessage)
                && (!askPending || bareMessage.contains("?"))) {
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
                        + truncate(qaDocs.get(0).toString(), 3500)
                        // The conversation itself is part of the draft's story — UI-handled
                        // turns (approval picks, saves) live only here, and "who did I add
                        // to the approval line?" must answer from them.
                        + "\nRECENT CONVERSATION:\n"
                        + String.join("\n", recentTurns(qaSession));
                String answer = formFollowUpAgentService.answerDraftQuestion(context, bareMessage, ko);
                return sideTurn(request, message, answer, "DRAFT_QUERY", List.of("FOLLOW_UP_AGENT"));
            }
        }
        // Taking someone OFF the traveller list is a change to THIS draft, so it outranks every
        // custom agent — a trip-lookup agent was claiming "김도하는 출장자에서 빼줘" and
        // answering with unrelated trips while the traveller stayed on the plan.
        if (midSession) {
            ConversationalAgentSession rmSession = resolveSession(request);
            ArrayNode rmDocs = documents(rmSession);
            ObjectNode rmState = loadState(rmSession);
            // The pending question comes first: a message that ANSWERS it is that answer,
            // whoever it happens to name.
            String drop = answersPendingAsk(rmSession, rmState, bareMessage, ko) ? null
                    : travellerToRemove(bareMessage, rmState, ko, recentTurns(rmSession),
                            bizplayToken);
            if (drop != null && !rmDocs.isEmpty()) {
                return removeTraveller(request, rmSession, rmState, rmDocs, drop, message,
                        bizplayToken, ko);
            }
        }
        // Chip tokens are OUR OWN generated text ("Trip type: <purpose> / <segment>"), not a user
        // request — routing them can hand the turn to an unrelated custom agent whose name happens
        // to resemble the purpose (e.g. a per-diem agent claiming "귀향교통비").
        // The STRIPPED message: the web chat prefixes a context block, so checking the raw
        // text never matched and the chip fell through to the router — where an agent
        // named after the segment ("귀향교통비") claimed it and answered with a tool error.
        boolean chipToken = bareMessage.toLowerCase(java.util.Locale.ROOT)
                .startsWith("trip type:");
        if (!message.isBlank() && !chipToken && !askPending && !(midSession && shortAnswer)) {
            CustomAgentService.RoutedReply custom = customAgentService.tryHandle(request.getCorpNo(), message);
            if (custom != null && midSession && aboutThisDraft(bareMessage, ko)) {
                // An OPEN draft owns anything about itself. A staff-lookup agent claimed
                // "출장자에 김도하도 추가해줘" and answered with an employee record while the
                // traveller was never added to the plan. Judged, not word-matched, and only on
                // the rare turn a custom agent actually claims.
                log.info("Custom agent '{}' claimed '{}', but the open plan draft owns it.",
                        custom.agentName(), truncateForLog(message));
            } else if (custom != null) {
                return sideTurn(request, message, custom.reply(), "CUSTOM_AGENT",
                        List.of("CUSTOM:" + custom.agentName()));
            }
        }
        if ("DATA_QUERY".equals(guard.category()) && agentPromptService.isModuleEnabled("database-lookup")) {
            return dataQueryTurn(request, message, ko);
        }

        ConversationalAgentSession session = resolveSession(request);
        if (session.getStatus() == ConversationalAgentSession.AgentStatus.POSTED) {
            // This session's plan is already FILED — editing it further only corrupts the record
            // (a new destination overwrote the submitted draft's title while the rest stayed).
            // A new message means a NEW plan: roll over to a fresh session; the response carries
            // the new sessionId and both the UI and the root agent follow it.
            log.info("Plan session {} is POSTED — rolling over to a fresh plan conversation.",
                    session.getId());
            request.setSessionId(null);
            session = resolveSession(request);
        }
        ObjectNode state = loadState(session);
        ArrayNode documents = documents(session);
        // The NET turn signature: what the draft (and the traveller/place slots) looked like when
        // this turn began. The mapper echoes history values every turn — overwriting the composed
        // title with a stale one, which ensureMeaningfulTitle then restores — so "did this turn
        // change anything" can only be judged end-to-end, never inside a single step.
        // A client that already asked for the 출장지 상세 sends it here — including "" for
        // "asked and skipped", which suppresses the server-side ask.
        if (request.getDestinationDetail() != null) {
            state.put("destinationDetail", request.getDestinationDetail().trim());
        }
        JsonNode turnStartDoc = strippedDoc(documents);
        // Full snapshots (not the stripped comparison copy): the alignment gate rewinds to
        // exactly here before a second attempt, so a retry can never double-apply the turn.
        final ArrayNode docsAtTurnStart = documents.deepCopy();
        final ObjectNode stateAtTurnStart = state.deepCopy();
        String turnStartSignature = turnSignature(documents, state);
        // Also the fallback for a ROUTE turn: the field mapper, seeing the site names in
        // "김도하는 비즈플레이에서 …", offers one as the trip destination — this is what
        // gets put back (see restoreDestinationAfterRoute).
        String destAtTurnStart = state.path("destination").asText("");
        // Same reason, for the other two slots a route sentence can leak into.
        final JsonNode detailAtTurnStart = state.path("destinationDetail").deepCopy();
        final JsonNode placesAtTurnStart = state.path("periodPlaces").deepCopy();

        String intent;
        String clarify = null;   // set when the user's input was indefinite — we ask, not guess
        // Set when THIS turn already composed the "which city in <country>?" question — the
        // readiness ask must not wipe/replace it with the generic region complaint.
        boolean countryCityAsked = false;
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
                clarify = fillFields(document, state, stagedPlus(state, turnText), subAgents, reply, ko,
                        recentTurns(session));
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
                // Changing the trip type mid-conversation: chips when the user hasn't named
                // the target, a full form switch (facts preserved) when they have.
                BizplayPlanAgentResponse switched = maybeSwitchTripType(request, session,
                        state, documents, message, turnText, bizplayToken, ko);
                if (switched != null) {
                    return switched;
                }
                // Form already loaded: every turn is field completion on documents[0].
                intent = "FIELD_COMPLETION";
                clarify = fillFields((ObjectNode) documents.get(0), state, turnText, subAgents, reply, ko,
                        recentTurns(session));
            }
        }

        // A traveller CORRECTION, before the resolver runs. mergeTravelers can only append, so
        // "출장자를 김도하로 바꿔줘" mid-flow GREW the list (김충북, 김도하) instead of fixing it,
        // and nothing could ever remove a wrong name. Runs after fillFields on purpose: the mapper
        // may have appended the new name, and the replace below settles the final list.
        if (!documents.isEmpty()) {
            backfillTravelerChange(state, turnText, reply, ko);
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
        // The previous turn ASKED "which country/city?" (DESTINATION_ASK). The reply to that
        // question IS the destination — bind it against the region master deterministically
        // instead of hoping the extractor notices the place ("일본 도쿄입니다" filled the
        // title but left the destination slot stuck on "해외").
        if (!documents.isEmpty() && state.path("pendingDestinationAsk").asBoolean(false)) {
            // The ANSWER is the user's own words — the chat-context prefix carries earlier
            // place names, and matching against it re-bound the OLD destination on a turn
            // that was about dates ("this month from 4 to 6" replied "Destination set to
            // 뉴샤텔" out of nowhere).
            String destAnswer = stripChatContext(turnText);
            JsonNode hit = destinationResolverAgentService.resolveDestinationText(documents, destAnswer, bizplayToken);
            boolean confirmed = hit != null && !hit.path("name").asText("").isBlank();
            if (!confirmed) {
                // A typed COUNTRY ("스위스", "일본으로") is half an answer, not a wrong one —
                // reply with THAT country's city question instead of "isn't on the list".
                JsonNode countryHit = destinationResolverAgentService.resolveCountryText(
                        documents, destAnswer, bizplayToken);
                if (countryHit != null && !countryHit.path("name").asText("").isBlank()) {
                    String cname = countryHit.path("name").asText("");
                    StringBuilder sample = new StringBuilder();
                    int shown = 0;
                    for (JsonNode city : countryHit.path("cities")) {
                        sample.append(shown++ > 0 ? ", " : "").append(city.asText(""));
                        if (shown >= 8) {
                            break;
                        }
                    }
                    state.put("destinationCountry", cname);
                    reply.append(t(ko,
                            cname + " it is — which city there? "
                                    + (sample.length() > 0 ? "(e.g. " + sample + ") " : ""),
                            cname + "(으)로 가시는군요 — 어느 도시인가요? "
                                    + (sample.length() > 0 ? "(예: " + sample + ") " : "")));
                    // The ask stays pending; the next message binds the city.
                    countryCityAsked = true;
                    log.info("Destination ask answered with a COUNTRY ({}) — asking its city.", cname);
                }
            }
            if (confirmed) {
                String name = hit.path("name").asText("");
                // Negation veto: a bare answer ("도쿄", "도쿄요") binds as-is, but a name
                // matched out of a longer SENTENCE may be mentioned without being chosen
                // ("도쿄는 아니고 날짜부터 바꿀게"). String matching cannot read the "아니고" —
                // the semantic judge confirms the choice before the bind applies.
                if (destAnswer.trim().length() > name.length() + 6) {
                    Integer pick = destinationResolverAgentService.pickDestination(java.util.List.of(name),
                            destAnswer,
                            "The assistant asked which country/city the trip goes to. Choose this "
                                    + "option ONLY if the user's message actually selects or confirms "
                                    + "it as the destination — NOT if the message rejects it, mentions "
                                    + "it in passing, or is about something else.",
                            ko);
                    confirmed = pick != null;
                    if (!confirmed) {
                        log.info("Negation veto: '{}' mentions {} but does not choose it — "
                                + "bind cancelled, ask stays pending.",
                                truncateForLog(turnText), name);
                    }
                }
            }
            if (confirmed) {
                String name = hit.path("name").asText("");
                writeDestination(documents, state, name);
                if (!hit.path("countryName").asText("").isBlank()) {
                    state.put("destinationCountry", hit.path("countryName").asText(""));
                }
                state.remove("pendingDestinationAsk");
                reply.append(t(ko, "Destination set to " + name + ". ",
                        "출장지를 " + name + "(으)로 설정했어요. "));
                log.info("Destination ask answered deterministically: '{}' -> {}",
                        truncateForLog(turnText), name);
            }
        }
        // The user's own sentence only — the chat prefixes a context block that mentions
        // "business trip", "Still needed: …" and the destination, none of it an answer.
        String saidWords = stripChatContext(turnText);
        backfillOrigin(state, saidWords);
        backfillTransport(state, saidWords);
        // 이동경로: a route between the company's registered destinations, read from the user's
        // own words. Checked on EVERY turn — they may name it up front ("비즈플레이에서 티엑스알
        // 로보틱스 본사 갔다가 복귀") long before anyone asks — and bound when the agent did ask.
        List<String> tripTravellers = travelerNames(state) == null
                ? List.<String>of() : travelerNames(state);
        // A per-traveller route can arrive at ANY time — including after the trip-wide one is
        // already set ("김도하는 부산 쪽으로 갔다가 복귀") — so this block keeps listening.
        boolean routeStillOpen = state.path("routePoints").isMissingNode()
                || tripTravellers.size() > 1;
        if (!documents.isEmpty() && routeStillOpen && !turnText.isBlank()) {
            boolean askedRoute = state.path("pendingRouteAsk").asBoolean(false);
            if (askedRoute || turnText.length() >= 8) {
                // The USER's own sentence only. The UI prefixes every message with a context
                // block ("(Current user: 김충북 — …)"), and reading that as part of the answer
                // pinned every typed route on whoever is signed in — so a route meant for the
                // whole trip became one person's, and the question asked itself again.
                JsonNode route = planEnrichmentService.resolveRoutePoints(
                        stripChatContext(turnText), tripTravellers, bizplayToken, ko);
                String forWhom = route == null ? "" : route.path("traveller").asText("");
                if (route != null && route.path("points").isArray() && !forWhom.isBlank()) {
                    // Named person, named route: theirs alone. Everyone else keeps the trip's.
                    state.with("routePointsByTraveller")
                            .set(forWhom, route.path("points").deepCopy());
                    state.remove("pendingRouteAsk");
                    StringBuilder path = new StringBuilder();
                    for (JsonNode p : route.path("points")) {
                        path.append(path.length() == 0 ? "" : " → ").append(p.asText(""));
                    }
                    reply.append(t(ko, forWhom + "'s route: " + path + ". ",
                            forWhom + " 님의 이동경로를 " + path + "(으)로 정했어요. "));
                    state.put("routeTurn", true);
                    state.set("routeTurnPoints", route.path("points").deepCopy());
                    restoreDestinationAfterRoute(documents, state, destAtTurnStart, route);
                    log.info("Route for {} captured from '{}': {}", forWhom,
                            truncateForLog(turnText), route.path("points"));
                } else if (route != null && route.path("points").isArray()
                        && state.path("routePoints").isMissingNode()) {
                    state.set("routePoints", route.path("points").deepCopy());
                    state.remove("pendingRouteAsk");
                    StringBuilder path = new StringBuilder();
                    for (JsonNode p : route.path("points")) {
                        path.append(path.length() == 0 ? "" : " → ").append(p.asText(""));
                    }
                    reply.append(t(ko, "Route set: " + path + ". ",
                            "이동경로를 " + path + "(으)로 정했어요. "));
                    state.put("routeTurn", true);
                    state.set("routeTurnPoints", route.path("points").deepCopy());
                    restoreDestinationAfterRoute(documents, state, destAtTurnStart, route);
                    log.info("Route captured from '{}': {}", truncateForLog(turnText),
                            route.path("points"));
                } else if (askedRoute) {
                    // "skip" is an ANSWER: the question closes now, not after two misses. Judged
                    // (never a word list) and only on a turn that already failed to name a route,
                    // so it costs a call on the rare miss turn alone.
                    boolean declinedRoute = "yes".equalsIgnoreCase(slotFillerAgentService.extract(
                            stripChatContext(turnText), java.util.Map.of("skipThis",
                                    "\"yes\" ONLY if the user DECLINES to answer the question just "
                                            + "asked - skip it, leave it, move on, next, don't "
                                            + "know, doesn't matter - instead of answering it"),
                            ko, recentTurns(session)).path("skipThis").asText(""));
                    if (declinedRoute) {
                        state.put("routeAskSkipped", true);
                        state.remove("pendingRouteAsk");
                        log.info("Route ask skipped - the user declined to answer it.");
                    }
                    // The answer named no registered site. The route is optional — the automatic
                    // origin → destination → origin round trip fills the item — so the question
                    // steps aside after two misses instead of blocking the plan forever.
                    int misses = declinedRoute ? 0 : state.path("routeAskMisses").asInt(0) + 1;
                    state.put("routeAskMisses", misses);
                    if (!declinedRoute && misses >= 2) {
                        state.put("routeAskSkipped", true);
                        state.remove("pendingRouteAsk");
                        log.info("Route ask skipped after {} unresolved answers — the round trip "
                                + "between origin and destination fills the item.", misses);
                    }
                }
            }
        }
        // Same ask-then-bind treatment as the destination: when the agent itself asked for
        // the transport (readiness ask) or the route/출발지 (follow-up), the answer is bound
        // by a FOCUSED extraction — the deterministic backfills above run first and win; the
        // slot-filler only covers what they missed ("shinkansen", a bare "인천이요").
        boolean needTransport = state.path("pendingTransportAsk").asBoolean(false)
                && state.path("transportType").asText("").isBlank();
        boolean needOrigin = state.path("pendingOriginAsk").asBoolean(false)
                && state.path("origin").asText("").isBlank();
        boolean needDetail = state.path("pendingDetailAsk").asBoolean(false);
        if (!documents.isEmpty() && (needTransport || needOrigin || needDetail)) {
            java.util.Map<String, String> wanted = new java.util.LinkedHashMap<>();
            if (needTransport) {
                wanted.put("transportType", "how the user will travel, as ONE of: "
                        + "PUBLIC_AIRLINE (비행기/flight), PUBLIC_TRAIN (기차/KTX/train), "
                        + "PUBLIC_BUS (버스), PUBLIC (대중교통), TAXI, RENTAL_CAR (렌터카), "
                        + "CORP_CAR (법인차), PRIVATE (자차/own car)");
            }
            if (needOrigin) {
                wanted.put("origin", "the departure place (출발지) the user names — "
                        + "a city, station, airport or office");
            }
            if (needDetail) {
                wanted.put("destinationDetail", "a short specific detail about the place at "
                        + "the DESTINATION (a building, floor, venue, branch). OMIT this slot "
                        + "entirely if the user declines, says there is none, or talks about "
                        + "something else");
                wanted.put("detailDeclined", "\"yes\" ONLY if the user says there is no "
                        + "destination detail or declines to give one");
            }
            if (needDetail || needTransport) {
                wanted.put("proceedNow", "\"yes\" ONLY if the user asks to create/submit/"
                        + "save the plan, or to finish/move on, instead of answering");
                // Declining is an ANSWER too. "skip" is not "file the plan", so it never
                // matched proceedNow and the same question came back every turn.
                wanted.put("skipThis", "\"yes\" ONLY if the user DECLINES to answer the question "
                        + "just asked - skip it, leave it, don't know, doesn't matter "
                        + "(skip, next, pass, whatever, 넘어가, 건너뛰, 몰라, 상관없어) - instead of "
                        + "answering it with a value");
            }
            JsonNode got = slotFillerAgentService.extract(turnText, wanted, ko, recentTurns(session));
            // A trip-type switch REPLAYS our own chip text ("Trip type: 해외출장 · 장기") through
            // this pipeline. Those are not the user's words, and reading "move on" out of them
            // skipped the transport and route questions on the new form without anyone asking.
            boolean typeReplay = stripChatContext(message).trim()
                    .toLowerCase(java.util.Locale.ROOT).startsWith("trip type:");
            boolean proceed = !typeReplay
                    && "yes".equalsIgnoreCase(got.path("proceedNow").asText(""));
            // A decline closes THIS question only - unlike "create it now", which closes them all.
            boolean skipThis = !typeReplay
                    && "yes".equalsIgnoreCase(got.path("skipThis").asText(""));
            String tv = got.path("transportType").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            if (needTransport && !tv.isBlank()) {
                if (java.util.Set.of("PUBLIC_AIRLINE", "PUBLIC_TRAIN", "PUBLIC_BUS", "PUBLIC",
                        "TAXI", "RENTAL_CAR", "CORP_CAR", "PRIVATE").contains(tv)) {
                    state.put("transportType", tv);
                    log.info("Transport ask answered via focused extraction: '{}' -> {}",
                            truncateForLog(turnText), tv);
                } else {
                    // The model answered in words, not the enum — the word map reads it.
                    backfillTransport(state, tv);
                }
            }
            if (needTransport && state.path("transportType").asText("").isBlank()
                    && skipThis && !proceed) {
                // "skip": this question is done, the rest of the flow carries on. The legs keep
                // the paper's default vehicle (flight for an overseas trip), which is what the
                // preview already shows.
                state.put("transportAskSkipped", true);
                state.remove("pendingTransportAsk");
                log.info("Transport ask skipped - the user declined to answer it.");
            }
            if (needTransport && state.path("transportType").asText("").isBlank()
                    && proceed) {
                // Proceeding without naming a vehicle: the route falls back to the paper's
                // default (flight for overseas) instead of the question blocking the flow.
                state.put("transportAskSkipped", true);
                state.remove("pendingTransportAsk");
                // Proceeding skips EVERY remaining optional ask, not one per turn.
                if (!state.has("destinationDetail")) {
                    state.put("destinationDetail", "");
                    state.remove("pendingDetailAsk");
                }
                if (state.path("routePoints").isMissingNode()) {
                    state.put("routeAskSkipped", true);   // the automatic round trip fills it
                    state.remove("pendingRouteAsk");
                }
                log.info("Transport ask skipped — the user asked to proceed; routes will default.");
            }
            String ov = got.path("origin").asText("").trim();
            if (needOrigin && !ov.isBlank()) {
                state.put("origin", ov);
                log.info("Origin ask answered via focused extraction: '{}' -> {}",
                        truncateForLog(turnText), ov);
            }
            if (needDetail) {
                // Three-way: a value fills it, an explicit decline skips it ("" marks
                // "asked"), and a MISS keeps the ask pending so a flaky extraction cannot
                // silently discard the answer — capped at two misses, then auto-skip.
                String dv = got.path("destinationDetail").asText("").trim();
                boolean declined = "yes".equalsIgnoreCase(got.path("detailDeclined").asText(""))
                        || skipThis;
                if (!dv.isBlank()) {
                    state.put("destinationDetail", dv);
                    state.remove("pendingDetailAsk");
                    log.info("Destination detail captured: '{}'", dv);
                } else if (declined) {
                    state.put("destinationDetail", "");
                    state.remove("pendingDetailAsk");
                } else if (proceed) {
                    // "create the plan" IS the answer: no detail, move on. The optional
                    // ask must never stand between the user and filing.
                    state.put("destinationDetail", "");
                    state.remove("pendingDetailAsk");
                    if (state.path("transportType").asText("").isBlank()) {
                        state.put("transportAskSkipped", true);
                        state.remove("pendingTransportAsk");
                    }
                    log.info("Detail ask skipped — the user asked to proceed.");
                } else {
                    int misses = state.path("detailAskMisses").asInt(0) + 1;
                    state.put("detailAskMisses", misses);
                    if (misses >= 2) {
                        state.put("destinationDetail", "");   // stop asking — optional field
                        state.remove("pendingDetailAsk");
                    }
                    log.info("Detail ask not answered (miss {}) — {}", misses,
                            misses >= 2 ? "auto-skipped" : "will re-ask");
                }
                // The mapper parrots the detail answer into the DESTINATION ("오사카 본사
                // 3층" replaced 도쿄). While the detail ask is pending, a destination
                // change is only kept if it is NOT the detail text and the judge confirms
                // the user really moved the trip; otherwise the turn-start value returns.
                String ndest = state.path("destination").asText("");
                if (!ndest.equals(destAtTurnStart) && !destAtTurnStart.isBlank()) {
                    String dvNow = state.path("destinationDetail").asText("");
                    boolean isDetailText = !dvNow.isBlank()
                            && (ndest.equals(dvNow) || ndest.contains(dvNow) || dvNow.contains(ndest));
                    // Same data gate as the general change-judge: a destination overwrite is
                    // only ever genuine if the new value names a region-master entry. A parrot
                    // like "없어" (from "세부 목적지는 없어") resolves to nothing and restores
                    // without consulting the judge — the master decides, not phrasing.
                    boolean namesRegion = !isDetailText
                            && destinationResolverAgentService.resolveDestinationText(
                                    documents, ndest, bizplayToken) != null;
                    boolean genuine = namesRegion && destinationResolverAgentService.pickDestination(
                            java.util.List.of(ndest), turnText,
                            "The assistant asked for an optional venue/building DETAIL, not a "
                                    + "new destination. Choose this option ONLY if the user is "
                                    + "actually CHANGING the trip destination to it.",
                            ko) != null;
                    if (!genuine) {
                        writeDestination(documents, state, destAtTurnStart);
                        log.info("Detail-turn parrot: destination '{}' restored to '{}'.",
                                ndest, destAtTurnStart);
                    }
                }
            }
            subAgents.add("SLOT_FILLER_AGENT");
        }
        // A filled slot ends its ask — by whichever path it was filled.
        if (!state.path("transportType").asText("").isBlank()) {
            state.remove("pendingTransportAsk");
        }
        if (!state.path("origin").asText("").isBlank()) {
            state.remove("pendingOriginAsk");
        }
        // Relative DATE edits are calendar arithmetic, not language understanding — the one
        // turn the shadow verifier flagged in every sweep ("시작일을 하루 늦춰줘" → phantom
        // clarify). Deterministic: endpoint × direction × amount, computed from the
        // TURN-START dates so a mapper that also fires cannot double-shift.
        boolean dateEdited = backfillDateEdit(documents, turnStartDoc, reply,
                stripChatContext(turnText), ko);
        // Per-day destinations: the company form gives EVERY period row its own
        // country/city ("첫날은 랑바레네, 둘째 날은 티메리") — captured here whenever the
        // message pairs days with resolvable places.
        capturePeriodDestinations(documents, state, turnText, subAgents, reply, ko, bizplayToken);
        // A message that was just read as a TRAVEL ROUTE names the company's own sites
        // ("김도하는 비즈플레이에서 티엑스알로보틱스(부산) …") — never the trip's country/city.
        // Letting the destination backfill loose on it replaced 오사카 with an office name and
        // then complained that the office is not an allowed region.
        boolean routeTurn = state.path("routeTurn").asBoolean(false);
        state.remove("routeTurn");
        boolean destBackfilled = routeTurn
                || backfillDestination(documents, state, turnText, bizplayToken);
        // LLM change-judge — the GENERAL path for destination edits, any phrasing, any
        // language ("change to tokyo", "let's do Nagoya instead", "도쿄가 낫겠다").
        // Grammar above is only the zero-cost fast lane; when it misses, this decides.
        // Gate is data-driven, not phrasing: the message must name a region-master entry
        // that DIFFERS from the current destination. The judge then answers one question —
        // is the user CHANGING the trip to it? — which also keeps negations and passing
        // mentions out ("도쿄는 아니고", "the Tokyo team asked...").
        if (!destBackfilled && !documents.isEmpty()
                && !state.path("pendingDestinationAsk").asBoolean(false)) {
            String bareText = stripChatContext(turnText);
            JsonNode regionHit = destinationResolverAgentService.resolveDestinationText(
                    documents, bareText, bizplayToken);
            if (regionHit == null) {
                // The paper may RESTRICT regions (the 장기 form has no 도쿄) — but a real
                // city outside its list is still a change request. Detect it against the
                // UNRESTRICTED master; applying it lets the readiness ask explain the
                // restriction instead of the change being silently dropped.
                regionHit = destinationResolverAgentService.resolveRegion(
                        bareText, "OVERSEA", true, false, bizplayToken);
            }
            String hitName = regionHit == null ? "" : regionHit.path("name").asText("");
            String curDest = state.path("destination").asText("");
            if (!hitName.isBlank() && !hitName.equalsIgnoreCase(curDest)
                    && !hitName.equalsIgnoreCase(destAtTurnStart)
                    && !curDest.toLowerCase(java.util.Locale.ROOT)
                            .contains(hitName.toLowerCase(java.util.Locale.ROOT))) {
                Integer verdict = destinationResolverAgentService.pickDestination(
                        java.util.List.of(hitName), bareText,
                        "The trip's destination is currently \"" + (curDest.isBlank()
                                ? "(not set)" : curDest) + "\". Choose this option ONLY if "
                                + "the user's message CHANGES or SETS the trip destination "
                                + "to it — NOT if the place is merely mentioned, rejected, "
                                + "compared, or part of an address, venue or day-specific "
                                + "detail.", ko);
                if (verdict != null) {
                    writeDestination(documents, state, hitName);
                    if (!regionHit.path("countryName").asText("").isBlank()) {
                        state.put("destinationCountry", regionHit.path("countryName").asText(""));
                        state.put("destinationCountryFor", hitName);
                    }
                    destBackfilled = true;   // same clarify-drop as the grammar path
                    log.info("Destination change judged from '{}': -> {}",
                            truncateForLog(bareText), hitName);
                }
            }
        }
        // GENERAL destination guard (every turn, not just pending-ask ones): once a trip has a
        // resolved destination, the mapper may only replace it with another region-master hit or
        // with the judge's confirmation. Without this, side-band phrases were parroted straight
        // into the field — 'add destination detail of "floor 2"' moved the trip to "floor 2".
        // When the overwrite is vetoed, the turn is re-read for what it usually was: a
        // destination-DETAIL edit, bound to its own slot.
        String guardDest = state.path("destination").asText("");
        if (!documents.isEmpty() && !destBackfilled && !destAtTurnStart.isBlank()
                && !guardDest.isBlank() && !guardDest.equals(destAtTurnStart)
                && destinationResolverAgentService.resolveDestinationText(
                        documents, guardDest, bizplayToken) == null) {
            Integer keep = destinationResolverAgentService.pickDestination(
                    java.util.List.of(guardDest), turnText,
                    "The trip already has the destination " + destAtTurnStart + ". Choose this "
                            + "option ONLY if the user is actually MOVING the trip to it — not "
                            + "when the phrase is a venue/building/floor note, a detail, or "
                            + "about something else.", ko);
            if (keep == null) {
                writeDestination(documents, state, destAtTurnStart);
                log.info("Mapper destination overwrite '{}' vetoed — '{}' restored.",
                        guardDest, destAtTurnStart);
                // What the turn actually carried, judged by the model: a short venue/building
                // detail rides in its own slot (the period rows' selectionMemo at save).
                JsonNode dgot = slotFillerAgentService.extract(turnText, java.util.Map.of(
                        "destinationDetail", "a short venue/building/floor note about the place "
                                + "at the destination the user wants recorded (up to 10 "
                                + "characters, e.g. \"floor 2\", \"본사 3층\"). OMIT when the "
                                + "message carries no such note"),
                        ko, recentTurns(session));
                String dnote = dgot.path("destinationDetail").asText("").trim();
                if (!dnote.isBlank()) {
                    state.put("destinationDetail", dnote);
                    state.remove("pendingDetailAsk");
                    reply.append(t(ko,
                            "Noted the destination detail: \"" + dnote + "\". ",
                            "출장지 상세를 \"" + dnote + "\"(으)로 기록했어요. "));
                    log.info("Destination detail edit captured: '{}'", dnote);
                }
            }
        }
        if (destBackfilled || dateEdited
                || !state.path("destination").asText("").equals(destAtTurnStart)) {
            // The turn DID change the destination — by the backfill OR by the mapper itself.
            // Keeping the mapper's clarify would bury that: on "목적지를 도쿄로 바꿔줘" it
            // hallucinates an indefinite-DATE question ("다음 주 며칠로 바꿔 드릴까요?") even
            // when the message says nothing about dates, and the clarify short-circuits the
            // readiness re-check. The change is concrete; the phantom question goes.
            clarify = null;
        }
        ensureMeaningfulTitle(state, documents, turnText, ko);
        // The mapper parrots a short answer into EVERY text field it can reach — "japan" became
        // destination AND title AND content, and the wizard stopped asking for the purpose. A
        // title/content that is nothing but the destination word is no answer: recompose the
        // title, clear the content so the purpose question is actually asked.
        if (!documents.isEmpty()) {
            ObjectNode d0 = (ObjectNode) documents.get(0);
            String destWord = state.path("destination").asText("").trim();
            if (!destWord.isBlank()) {
                if (d0.path("content").asText("").trim().equalsIgnoreCase(destWord)) {
                    d0.put("content", "");
                }
                if (d0.path("title").asText("").trim().equalsIgnoreCase(destWord)) {
                    String composed = destWord + " " + t(ko, "trip", "출장");
                    d0.put("title", composed);
                    state.put("composedTitle", composed);
                }
            }
        }

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
                // The form is done, so what does THIS message want? Decided by the LLM with the
                // recent turns as context, not a phrase list: after "the last step is the approval
                // line", a "create plan" / "done" / "그럼 올려줘" is the user saying GO — and the
                // UI kept re-showing the approval card because nothing understood that. The agent
                // cannot file by itself (filing is the separate /create call, on purpose), so it
                // answers with intent SUBMIT_REQUESTED and the caller runs the create it already
                // has — with whatever approval line the user picked.
                // Only when this turn changed NOTHING. A turn that edited the draft is an edit,
                // full stop - yet the model judged "출장자는 김비플이야" and "김도하는 빼줘" as
                // file-now, which would have filed mid-correction. Anything applied or warned
                // about earlier in the turn leaves text in the reply, so an empty reply IS the
                // deterministic "nothing happened" signal.
                // "This turn changed nothing" is judged END-TO-END (draft + traveller/place
                // slots), not by whether some step talked: the mapper's history echo rewrites
                // and restores values within a single turn, leaving text in the reply while the
                // net draft is identical — which silently blocked every "이대로 제출해줘".
                boolean docChanged = !strippedDoc(documents).equals(turnStartDoc);
                boolean slotsChanged = !turnSignature(documents, state).equals(turnStartSignature);
                boolean turnChangedSomething = docChanged || slotsChanged;
                // The 출장지 상세 has no form field, so the mapper often ignores an edit to it
                // outright ('add destination detail of "floor 2"' changed nothing). A completed
                // form's no-change turn gets one focused read for that slot; the answer must
                // literally appear in the message (anti-hallucination), the model decides the
                // meaning. When it lands, the turn DID change something.
                if (!turnChangedSomething && !turnText.isBlank()) {
                    String bareTurn = stripChatContext(turnText);
                    JsonNode dgot = slotFillerAgentService.extract(bareTurn, java.util.Map.of(
                            "destinationDetail", "a short venue/building/floor note about the "
                                    + "place at the destination the user wants recorded (up to "
                                    + "10 characters, e.g. \"floor 2\", \"본사 3층\"). OMIT "
                                    + "when the message carries no such note"),
                            ko, recentTurns(session));
                    String dnote = dgot.path("destinationDetail").asText("").trim();
                    if (!dnote.isBlank() && bareTurn.contains(dnote)
                            && !dnote.equals(state.path("destinationDetail").asText(""))) {
                        state.put("destinationDetail", dnote);
                        state.remove("pendingDetailAsk");
                        reply.append(t(ko,
                                "Noted the destination detail: \"" + dnote + "\". ",
                                "출장지 상세를 \"" + dnote + "\"(으)로 기록했어요. "));
                        turnChangedSomething = true;
                        log.info("Destination detail edit captured on no-change turn: '{}'", dnote);
                    }
                }
                log.info("Turn '{}' changed the draft: {} (doc={}, slots={})",
                        truncateForLog(turnText), turnChangedSomething, docChanged, slotsChanged);
                if (slotsChanged) {
                    log.info("  slots before: {}", turnStartSignature);
                    log.info("  slots after : {}", turnSignature(documents, state));
                }
                if (docChanged) {
                    ObjectNode before = (ObjectNode) turnStartDoc;
                    ObjectNode after = strippedDoc(documents);
                    java.util.Set<String> keys = new java.util.LinkedHashSet<>();
                    before.fieldNames().forEachRemaining(keys::add);
                    after.fieldNames().forEachRemaining(keys::add);
                    for (String k : keys) {
                        if (before.path(k).equals(after.path(k))) {
                            continue;
                        }
                        if ("issuedItems".equals(k) && before.path(k).isArray()
                                && before.path(k).size() == after.path(k).size()) {
                            for (int ii = 0; ii < before.path(k).size(); ii++) {
                                JsonNode ib = before.path(k).get(ii);
                                JsonNode ia = after.path(k).get(ii);
                                if (!ib.equals(ia)) {
                                    log.info("  issuedItems[{}] '{}' changed: {} -> {}", ii,
                                            ib.path("item").path("name").asText("?"),
                                            ib.toString().length() > 300
                                                    ? ib.toString().substring(0, 300) : ib.toString(),
                                            ia.toString().length() > 300
                                                    ? ia.toString().substring(0, 300) : ia.toString());
                                }
                            }
                            continue;
                        }
                        log.info("  field '{}' changed: {} -> {}", k,
                                truncateForLog(before.path(k).toString()),
                                truncateForLog(after.path(k).toString()));
                    }
                }
                // The lookup-driven requirements from the provider's guide — a destination the
                // paper's region lists actually allow, a transport type when the route demands
                // one — are asked HERE, while the user can still answer, not discovered as a
                // refusal at create. One question at a time, same as every other gap.
                JsonNode readyAsk = countryCityAsked ? null
                        : planEnrichmentService.readinessAsk(documents, state, bizplayToken, ko);
                if (countryCityAsked) {
                    // The reply already asks "which city in <country>?" — keep it, keep the ask.
                    session.setStatus(ConversationalAgentSession.AgentStatus.COLLECTING);
                    intent = "DESTINATION_ASK";
                    state.put("pendingDestinationAsk", true);
                } else
                if (readyAsk != null) {
                    session.setStatus(ConversationalAgentSession.AgentStatus.COLLECTING);
                    reply.setLength(0);
                    reply.append(readyAsk.path("text").asText()).append(' ');
                    // The UI keys on the intent, not the wording: a region ask re-opens the
                    // country/city dropdown instead of leaving a bare text question — and the
                    // approval-line step must NOT start while this is unanswered.
                    if ("region".equals(readyAsk.path("kind").asText())) {
                        intent = "DESTINATION_ASK";
                        // Next turn's message is the answer to THIS question — flag it so
                        // the deterministic region-master binding picks it up.
                        state.put("pendingDestinationAsk", true);
                        state.remove("pendingTransportAsk");
                    } else if ("transport".equals(readyAsk.path("kind").asText())) {
                        state.put("pendingTransportAsk", true);
                        state.remove("pendingDestinationAsk");
                    } else if ("route".equals(readyAsk.path("kind").asText())) {
                        intent = "ROUTE_ASK";
                        state.put("pendingRouteAsk", true);
                        state.remove("pendingDestinationAsk");
                        state.remove("pendingTransportAsk");
                    } else if ("detail".equals(readyAsk.path("kind").asText())) {
                        state.put("pendingDetailAsk", true);
                        state.remove("pendingDestinationAsk");
                        state.remove("pendingTransportAsk");
                        state.remove("pendingRouteAsk");
                    } else {
                        state.remove("pendingDestinationAsk");
                        state.remove("pendingTransportAsk");
                    }
                } else {
                    state.remove("pendingDestinationAsk");   // resolved — the asks are over
                    state.remove("pendingTransportAsk");
                }
                if (readyAsk == null)
                if (!turnChangedSomething && wantsToFileNow(turnText, session, ko)) {
                    appendTurn(session, "user", message);
                    saveState(session, state);
                    ConversationalAgentSession savedNow = sessionRepo.save(session);
                    return BizplayPlanAgentResponse.builder()
                            .sessionId(savedNow.getId().toString())
                            .status(savedNow.getStatus() == null ? null : savedNow.getStatus().name())
                            .intent("SUBMIT_REQUESTED")
                            .subAgents(subAgents)
                            .reply(t(ko, "Filing the plan now.", "출장 계획을 상신할게요."))
                            .travelers(travelerNames(state))
                            .travelerIds(travelerIdList(state))
                            .destination(state.path("destination").asText(null))
                            .origin(state.path("origin").asText(null))
                            .draftJson(savedNow.getDraftJson())
                            .build();
                } else {
                    reply.append(t(ko,
                            "The form is all filled in — the last step is the approval line.",
                            "양식은 모두 채워졌어요 — 마지막으로 결재선만 정하면 돼요."));
                }
            } else {
                session.setStatus(ConversationalAgentSession.AgentStatus.COLLECTING);
                // A destination that cannot land in a region-bound form's lists is a REQUIRED
                // gap too — asked FIRST, not discovered after every other field is done. Without
                // this, a scope word the mapper grabbed ("overseas business trip" -> destination
                // "Overseas") sat on the card looking filled until the final readiness check.
                // Only the region kind may preempt; transport/detail stay end-of-flow.
                if (countryCityAsked) {
                    // The reply already carries "which city in <country>?" — nothing may bury it.
                    intent = "DESTINATION_ASK";
                    state.put("pendingDestinationAsk", true);
                } else {
                JsonNode regionAsk = planEnrichmentService.readinessAsk(documents, state, bizplayToken, ko);
                if (regionAsk != null && "region".equals(regionAsk.path("kind").asText())) {
                    reply.append(regionAsk.path("text").asText()).append(' ');
                    intent = "DESTINATION_ASK";
                    state.put("pendingDestinationAsk", true);
                    state.remove("pendingTransportAsk");
                } else {
                // ONE required field per turn: a wall of questions makes users skip some and
                // answer others out of order. The remaining gaps stay in missingFields for the
                // UI's progress display, but the agent only ASKS for the first one. Remember
                // WHICH field, so next turn's bare answer can be bound to it deterministically.
                List<String> askNow = List.of(missing.get(0));
                rememberPendingAsk(state, missing.get(0));
                // The 이동경로 question is also asking WHERE the trip starts — bind next
                // turn's answer to the origin slot with a focused extraction.
                boolean routeField = missing.get(0).contains("이동경로") || missing.get(0).contains("경로")
                        || missing.get(0).toLowerCase(java.util.Locale.ROOT).contains("route");
                if (routeField) {
                    state.put("pendingOriginAsk", true);
                }
                String more = missing.size() > 1
                        ? t(ko, " (" + (missing.size() - 1) + " more to go after this)",
                                " (이후 " + (missing.size() - 1) + "개 더 남았어요)")
                        : "";
                if (routeField) {
                    // "What is the travel route?" told the user nothing about what to answer.
                    // This field needs the DEPARTURE point (and ideally the transport) — say
                    // so explicitly, with an example, instead of letting the follow-up LLM
                    // phrase a vague one-liner.
                    reply.append(t(ko,
                            "Now the travel route — where will you depart from, and how will "
                                    + "you get there? For example: \"departing from Incheon "
                                    + "Airport, by plane\". The return leg is added "
                                    + "automatically.",
                            "이제 이동경로만 알려주세요 — 어디에서 출발해서 어떤 이동수단으로 "
                                    + "가시나요? 예: \"인천공항에서 출발해서 비행기로 가요\". "
                                    + "돌아오는 구간은 자동으로 추가돼요.")).append(more);
                } else if (agentPromptService.isModuleEnabled("form-follow-up")) {
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
            }
        }

        // Remember/refresh resolved traveler ids, then fan the master document out: one object per
        // traveler, exactly like the BizPlay save body (followers lack the drafter-only keys).
        if (request.getTravelerCorpUserIds() != null && !request.getTravelerCorpUserIds().isEmpty()) {
            state.set("travelerIds", objectMapper.valueToTree(request.getTravelerCorpUserIds()));
        }
        syncTravelerDocuments(documents, state, request.getCorpUserId());
        // Each traveller now HAS a document, so their legs can be written into it — the preview
        // then shows the real route, distances and total before anything is filed. Built HERE,
        // not when the route was captured: the fan-out clones the first traveller's document,
        // so legs written earlier were copied onto everyone.
        if (!state.path("routePoints").isMissingNode()
                || state.path("routePointsByTraveller").size() > 0) {
            planEnrichmentService.previewRoutes(documents, state, bizplayToken);
        }

        // The preview card shows Country/City the moment the destination is KNOWN — not only
        // after the readiness check finally runs (on route-carrying forms that could be
        // several turns later, and the card said just "Destination Osaka" meanwhile).
        // Deterministic and cached: region-master string matching, no LLM call.
        String destNow = state.path("destination").asText("").trim();
        if (!destNow.isBlank()
                && !destNow.equals(state.path("destinationCountryFor").asText(""))) {
            JsonNode countryHit = destinationResolverAgentService.resolveDestinationText(
                    documents, destNow, bizplayToken);
            if (countryHit != null && !countryHit.path("countryName").asText("").isBlank()) {
                state.put("destinationCountry", countryHit.path("countryName").asText(""));
                // Display the master-data Korean label, not the user's spelling: "Osaka"
                // becomes 오사카 everywhere (card, title composition, save preview).
                String canonical = countryHit.path("name").asText("");
                if (!canonical.isBlank() && !canonical.equals(destNow)) {
                    writeDestination(documents, state, canonical);
                }
                state.put("destinationCountryFor", canonical.isBlank() ? destNow : canonical);
            } else {
                // Unresolvable (or domestic) destination — a stale country from a previous
                // value must not caption the new one.
                state.remove("destinationCountry");
                state.remove("destinationCountryFor");
            }
        }
        // Persist: draft_json = EXACTLY the request-body array; state rides in chat_event_json.
        state.put("lang", ko ? "ko" : "en");   // save-flow replies reuse the conversation language
        session.setDraftJson(documents);
        appendTurn(session, "user", turnText);   // includes extracted file facts for later context
        appendTurn(session, "assistant", reply.toString());
        saveState(session, state);
        sessionRepo.save(session);
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

        // ALIGNMENT GATE: before the reply goes out, the verifier is asked whether what the
        // turn DID matches what the user asked for. On a miss the draft is rewound to exactly
        // where this turn started and the turn runs once more, told what was missed — then the
        // second answer stands whatever it says. One correction, never a loop, and a verifier
        // that cannot answer (or is switched off) never holds anything up.
        if (agentPromptService.isModuleEnabled("turn-verifier") && !message.isBlank()
                && !message.toLowerCase(java.util.Locale.ROOT).startsWith("trip type:")) {
            boolean turnChanged = !turnSignature(documents, state).equals(turnStartSignature)
                    || !strippedDoc(documents).equals(turnStartDoc);
            if (turnChanged) {
                ObjectNode d0 = documents.isEmpty() ? objectMapper.createObjectNode()
                        : (ObjectNode) documents.get(0);
                final String vMsg = stripChatContext(message);
                final String vBefore = turnStartSignature + " | dates:"
                        + turnStartDoc.path("bstrStartDate").asText("") + "~"
                        + turnStartDoc.path("bstrEndDate").asText("");
                final String vAfter = turnSignature(documents, state) + " | dates:"
                        + d0.path("bstrStartDate").asText("") + "~"
                        + d0.path("bstrEndDate").asText("")
                        + " | title:" + d0.path("title").asText("");
                final String vReply = reply.toString().trim();
                final boolean vKo = ko;
                if (retryOfMisalignedTurn) {
                    // Already the second attempt — judge for the record only, never again act.
                    java.util.concurrent.CompletableFuture.runAsync(() ->
                            turnVerifierAgentService.verifyShadow(vMsg, vBefore, vAfter, vReply, vKo));
                } else {
                    com.api.bizplay_conversational.service.turnVerifierAgentService.TurnVerifierAgentService.Verdict verdict =
                            turnVerifierAgentService.verify(vMsg, vBefore, vAfter, vReply, vKo);
                    if (!verdict.aligned()) {
                        BizplayPlanAgentResponse corrected = retryMisalignedTurn(request, session,
                                docsAtTurnStart, stateAtTurnStart, verdict.issue(), bizplayToken);
                        if (corrected != null) {
                            return corrected;
                        }
                        // Rewind failed — fall through and answer with this attempt.
                    }
                }
            }
        }

        // A route sentence names SITES, and every place-shaped slot in the turn wants them: the
        // destination (restored above, as soon as the route is read), the 출장지 상세 and the
        // per-day place - both of which are filled LATER in the turn, so they are put back here,
        // once nothing else can write to them. "이동경로는 … 티엑스알로보틱스(본사)" was leaving
        // 본사 in the destination detail.
        // Every stop this plan's route names, whoever it belongs to - the detail below is checked
        // against all of them, on EVERY turn, because the mapper re-derives "본사" from the
        // conversation long after the turn that stated the route.
        ArrayNode planRoutePoints = objectMapper.createArrayNode();
        if (state.path("routePoints").isArray()) {
            planRoutePoints.addAll((ArrayNode) state.path("routePoints"));
        }
        if (state.path("routePointsByTraveller").isObject()) {
            state.path("routePointsByTraveller").forEach(list -> {
                if (list.isArray()) {
                    planRoutePoints.addAll((ArrayNode) list);
                }
            });
        }
        JsonNode turnRoute = state.path("routeTurnPoints").isArray()
                ? state.path("routeTurnPoints") : planRoutePoints;
        if (turnRoute.isArray() && turnRoute.size() > 0) {
            ObjectNode asRoute = objectMapper.createObjectNode();
            asRoute.set("points", planRoutePoints.size() > 0 ? planRoutePoints : turnRoute);
            JsonNode detailNow = state.path("destinationDetail");
            if (detailNow.isTextual() && !detailNow.asText("").isBlank()
                    && routeMentions(asRoute, detailNow.asText(""))) {
                if (detailAtTurnStart.isMissingNode()
                        || routeMentions(asRoute, detailAtTurnStart.asText(""))) {
                    state.remove("destinationDetail");
                } else {
                    state.set("destinationDetail", detailAtTurnStart.deepCopy());
                }
                log.info("Destination detail '{}' dropped - it is a stop on this plan's route.",
                        detailNow.asText(""));
            }
            if (!state.path("periodPlaces").equals(placesAtTurnStart)) {
                if (placesAtTurnStart.isMissingNode()) {
                    state.remove("periodPlaces");
                } else {
                    state.set("periodPlaces", placesAtTurnStart.deepCopy());
                }
                log.info("Per-day places restored - the turn stated a route, not where each day "
                        + "is spent.");
            }
            state.remove("routeTurnPoints");
        }

        // What changed, so the client redraws the right cards rather than guessing from the
        // reply text. Deterministic: the turn either edited the draft or it did not.
        List<String> uiRefresh = strippedDoc(documents).equals(turnStartDoc)
                ? null : List.of("all");
        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent(intent)
                .subAgents(subAgents)
                .uiRefresh(uiRefresh)
                // The legs must carry a vehicle to be previewable at all; when the user has not
                // named one, say so, so the preview can mark it as the default it is.
                .transportDefaulted(state.path("transportType").asText("").isBlank() ? true : null)
                .reply(reply.toString().trim())
                .pendingChoices(pendingChoices)
                .missingFields(missing.isEmpty() ? null : missing)
                .destinationCountry(state.path("destinationCountry").asText(null))
                .destinationDetail(state.has("destinationDetail")
                        ? state.path("destinationDetail").asText("") : null)
                .periodPlaces(periodPlacesForResponse(state))
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

        // Region (국가/도시 → selectionId), period times and 이동경로 — the values the provider's
        // own screen fills from lookup APIs, decided per paper (most forms need none of it).
        // Throws with a clear ask when the form REQUIRES a region we can't resolve.
        boolean ko = "ko".equals(state.path("lang").asText(null));
        List<String> enrichNotes = planEnrichmentService.enrich(documents, state, bizplayToken, ko);

        // POST the draft_json AS-IS (it already has the save-body structure).
        String providerResponse = bizplayGatewayService.postPlanDraft(documents, bizplayToken);
        log.info("Plan draft saved to BizPlay: {}", providerResponse);

        session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
        String reply = t(ko,
                "All done — your trip plan has been saved to BizPlay.",
                "완료됐어요 — 출장 계획이 BizPlay에 저장되었습니다.");
        if (!enrichNotes.isEmpty()) {
            reply = reply + " " + String.join(" ", enrichNotes);
        }
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
        // Values the CHAT captured but this payload doesn't carry — the transport the user named
        // ("기차로 이동할 거야"), the origin, a destination detail — ride over from the linked
        // chat session so the saved routes reflect the conversation, not just the defaults.
        mergeChatCapturedState(state, request.getAgentSessionId(), request.getCorpNo());
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

        if (request.getDestinationDetail() != null && !request.getDestinationDetail().isBlank()) {
            state.put("destinationDetail", request.getDestinationDetail().trim());
        }
        // Same region/period-time/route enrichment as the chat create — one save contract.
        planEnrichmentService.enrich(documents, state, bizplayToken, false);

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
                            List<String> subAgents, StringBuilder reply, boolean ko,
                            List<String> turns) {
        JsonNode mapped = fieldMapperAgentService.mapFields(text, state.path("fields"), turns);
        subAgents.add("FIELD_MAPPER_AGENT");
        // A sentence that was just read as a TRAVEL ROUTE names the company's own SITES
        // ("비즈플레이", "티엑스알로보틱스(부산)"). The mapper, seeing place names, offers one of
        // them as the trip's destination — replacing 오사카 with an office and then failing the
        // form's region check. The trip's destination is a region; a route point never is.
        String destKept = state.path("destination").asText("");
        boolean routeTurn = state.path("routeTurn").asBoolean(false);
        String startBefore = document.path("bstrStartDate").asText("");
        String endBefore = document.path("bstrEndDate").asText("");
        JsonNode periodBefore = null;
        for (JsonNode issued : document.withArray("issuedItems")) {
            if ("BSTR_PERIOD".equals(issued.path("item").path("itemType").asText(""))) {
                periodBefore = issued.deepCopy();
            }
        }
        JsonNode beforeApply = document.deepCopy();
        List<String> applied = new ArrayList<>(
                formValueWriterService.apply(document, state.path("fields"), state, mapped));
        // TWO of the mapper's outputs can carry a destination: the explicit DESTINATION field and
        // the one embedded in BSTR_PERIOD - and when they disagree (the period one is often stale,
        // echoed from staged text), whichever the writer applied LAST silently won. The result was
        // a draft going to 도쿄 titled 부산 출장. The EXPLICIT field is the user's answer, so once
        // everything is applied it gets the final word.
        for (JsonNode f : state.path("fields")) {
            String key = f.path("key").asText("");
            if (key.endsWith("DESTINATION") && mapped.hasNonNull(key)) {
                String explicit = mapped.get(key).isTextual()
                        ? mapped.get(key).asText("") : mapped.get(key).path("choice").asText("");
                if (!explicit.isBlank() && !explicit.equals(state.path("destination").asText(""))) {
                    state.put("destination", explicit);
                    formValueWriterService.refreshPeriod(document, state);
                    log.info("Destination re-asserted to '{}' - the BSTR_PERIOD echo had overwritten it.",
                            explicit);
                }
                break;
            }
        }
        if (routeTurn && !destKept.isBlank()
                && !destKept.equals(state.path("destination").asText(""))) {
            log.info("Destination '{}' restored — this turn stated a travel ROUTE, and '{}' is a "
                    + "registered site on it, not the trip's destination.",
                    destKept, state.path("destination").asText(""));
            state.put("destination", destKept);
            formValueWriterService.refreshPeriod(document, state);
        }
        applied.addAll(ensurePeriodFallback(document, state, text));
        applied.addAll(bindPendingAnswer(document, state, text, mapped));
        state.remove("staged"); // consumed
        // The mapper knows today's date, so on a turn that names NO date it can echo TODAY into
        // the document's bstrStart/EndDate header while the period selections keep the real trip
        // dates — "이제 제출해줘" turned a 9/26 trip into one ending today. A date write landing
        // exactly on today from a dateless message is an invention: what stood is restored.
        boolean dateless = !DATEISH.matcher(text).find();
        log.info("fillFields '{}': dateless={}", truncateForLog(text), dateless);
        if (dateless) {
            restoreInventedToday(document, "bstrStartDate", startBefore);
            restoreInventedToday(document, "bstrEndDate", endBefore);
            restoreInventedPeriodItem(document, periodBefore);
        }
        // A period value must BE a date, whatever the mapper writes: "이동경로는 인천-취리히…"
        // once landed 인천 in bstrStartDate, and every later parse of the draft crashed with
        // '인천T00:00:0'. Any non-ISO date write is reverted to the turn-start value.
        boolean periodPoisoned = false;
        for (String[] kv : new String[][]{{"bstrStartDate", startBefore}, {"bstrEndDate", endBefore}}) {
            String v = document.path(kv[0]).asText("");
            if (!v.isBlank() && !v.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                periodPoisoned = true;
                if (kv[1] == null || kv[1].isBlank()) {
                    document.remove(kv[0]);
                } else {
                    document.put(kv[0], kv[1]);
                }
                log.info("Non-date {} '{}' reverted to '{}'.", kv[0], v, kv[1]);
            }
        }
        if (periodPoisoned && periodBefore != null) {
            ArrayNode poisonedItems = document.withArray("issuedItems");
            for (int i = 0; i < poisonedItems.size(); i++) {
                if ("BSTR_PERIOD".equals(poisonedItems.get(i).path("item").path("itemType").asText(""))) {
                    poisonedItems.set(i, periodBefore.deepCopy());
                }
            }
        }
        // Reversed bare-day range: "9월 22일부터 20일까지" names ONE month, but the mapper
        // invented a NEXT-month end (9/22 → 10/20) rather than reading the days as reversed.
        // Grammar normalization: both days belong to the named month, ordered low → high.
        java.util.regex.Matcher rev = java.util.regex.Pattern.compile(
                "(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일\\s*부터\\s*(?!\\d{1,2}\\s*월)(\\d{1,2})\\s*일\\s*까지")
                .matcher(text);
        if (rev.find()) {
            int mo = Integer.parseInt(rev.group(1));
            int d1 = Integer.parseInt(rev.group(2));
            int d2 = Integer.parseInt(rev.group(3));
            if (mo >= 1 && mo <= 12 && d2 < d1 && d1 <= 31 && d2 >= 1) {
                java.time.LocalDate today0 = java.time.LocalDate.now();
                int yr = mo < today0.getMonthValue() - 6 ? today0.getYear() + 1 : today0.getYear();
                try {
                    java.time.LocalDate lo = java.time.LocalDate.of(yr, mo, d2);
                    java.time.LocalDate hi = java.time.LocalDate.of(yr, mo, d1);
                    document.put("bstrStartDate", lo.toString());
                    document.put("bstrEndDate", hi.toString());
                    for (JsonNode issued : document.withArray("issuedItems")) {
                        if ("BSTR_PERIOD".equals(issued.path("item").path("itemType").asText(""))
                                && issued.path("selections").size() == 1) {
                            ObjectNode row = (ObjectNode) issued.path("selections").get(0);
                            row.put("selectionName", lo.toString());
                            row.put("selectionErpCode", hi.toString());
                        }
                    }
                    log.info("Reversed day-range normalized: '{}' -> {} ~ {}", rev.group(), lo, hi);
                } catch (java.time.DateTimeException ignored) {
                    // impossible day for that month — leave whatever the mapper wrote
                }
            }
        }
        if (!applied.isEmpty() && document.equals(beforeApply)) {
            // The mapper sees the recent turns, so on a turn that says nothing new ("이대로
            // 제출해줘") it re-emits values it can read in the history — and the writer dutifully
            // re-writes them, all identical. That's an ECHO, not an edit: saying "I've filled it
            // in" for it also blocked the submit judge, which only listens when a turn changed
            // nothing. Same invariant as the settlement agent's expense slots.
            applied.clear();
        }
        if (!applied.isEmpty()) {
            // The UI shows every captured value in the plan summary — the reply just talks.
            reply.append(t(ko,
                    "I've filled in the details you gave me. ",
                    "말씀해 주신 내용을 계획에 반영했어요. "));
        }
        String clarify = mapped.path("clarify").asText(null);
        // Phantom-clarify guard: the mapper's clarify exists for INDEFINITE dates ("sometime
        // next week") — but it also hallucinates one for messages that mention no date at all
        // ("해외출장 장기 계획 만들어줘" got "which days next week exactly?"). A message with
        // no date-ish token cannot need a date clarified; the period follow-up asks properly.
        if (clarify != null && dateless) {
            log.info("Phantom clarify dropped (dateless turn): '{}'", truncateForLog(clarify));
            clarify = null;
        }
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
    /** First non-blank of the candidates — option shapes differ per item type. */
    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

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
        // A CHOICE field takes one of ITS OWN options, never free text. The user's next message
        // is often about something else entirely ("9월 21일부터 22일까지 부산 출장, 출장자
        // 김충북, KTX로" landed whole into 안전교육 이수여부), and a select carrying a sentence
        // is broken data the provider will happily store.
        if (field.path("options").size() > 0) {
            String picked = null;
            for (JsonNode o : field.path("options")) {
                String label = o.isTextual() ? o.asText()
                        : firstNonBlank(o.path("label").asText(""), o.path("name").asText(""),
                                        o.path("value").asText(""));
                if (!label.isBlank() && answer.contains(label)) {
                    // The longest matching option wins — "미이수" must beat "이수".
                    if (picked == null || label.length() > picked.length()) {
                        picked = label;
                    }
                }
            }
            if (picked == null) {
                log.info("Pending answer '{}' names none of {}'s options — leaving it unset.",
                        truncateForLog(answer), pendingKey);
                return List.of();
            }
            answer = picked;
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
        if (dates.isEmpty() && DATEISH.matcher(text).find()) {
            // LLM fallback for natural-language periods the broad mapper drops ("this month
            // from 4 to 6", "다음 주 수요일부터 금요일까지"): a focused extraction anchored to
            // TODAY answers in ISO, and the code only checks the arithmetic — parseable dates,
            // end not before start, within two years. The model resolves the meaning.
            java.time.LocalDate today = java.time.LocalDate.now();
            boolean ko = text.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
            // Weekday arithmetic is done HERE, not in the model's head — a computed calendar
            // of this week and next rides in the prompt ("다음 주 수요일" was answered one day
            // off without it).
            java.time.LocalDate mon = today.with(java.time.DayOfWeek.MONDAY);
            StringBuilder weeks = new StringBuilder("This week: ");
            for (int i = 0; i < 7; i++) {
                java.time.LocalDate d = mon.plusDays(i);
                weeks.append(d.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH))
                        .append('=').append(d).append(i < 6 ? ", " : ". Next week: ");
            }
            for (int i = 0; i < 7; i++) {
                java.time.LocalDate d = mon.plusWeeks(1).plusDays(i);
                weeks.append(d.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH))
                        .append('=').append(d).append(i < 6 ? ", " : ".");
            }
            JsonNode got = slotFillerAgentService.extract(text, java.util.Map.of(
                    "startDate", "the trip's START date as ISO YYYY-MM-DD. Today is " + today
                            + " (" + today.getDayOfWeek() + "). " + weeks + " Resolve relative "
                            + "phrasing ('this month from 4 to 6' means day 4 of the current "
                            + "month) — copy weekday dates from the calendar above. "
                            + "OMIT unless the message states when the trip starts",
                    "endDate", "the trip's END date as ISO YYYY-MM-DD, resolved the same way. "
                            + "OMIT unless stated"),
                    ko, java.util.List.<String>of());
            try {
                String s = got.path("startDate").asText("").trim();
                String e = got.path("endDate").asText("").trim();
                if (!s.isEmpty()) {
                    java.time.LocalDate sd = java.time.LocalDate.parse(s);
                    java.time.LocalDate ed = e.isEmpty() ? sd : java.time.LocalDate.parse(e);
                    if (!ed.isBefore(sd) && Math.abs(
                            java.time.temporal.ChronoUnit.DAYS.between(today, sd)) <= 730) {
                        dates.add(sd.toString());
                        dates.add(ed.toString());
                        log.info("Period resolved by focused extraction: '{}' -> {} ~ {}",
                                truncateForLog(text), sd, ed);
                    }
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // The model answered in words, not ISO — the follow-up asks properly.
            }
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

    /**
     * "목적지를 도쿄로", "출장지는 부산" — the destination named with its own explicit marker. Like
     * ORIGIN_PHRASE this is grammar normalisation, not intent guessing: the words 목적지/출장지 ARE
     * the field label, so a place attached to them is that field's value, whatever the sentence
     * around it says.
     */
    private static final java.util.regex.Pattern DEST_PHRASE = java.util.regex.Pattern.compile(
            "(?:목적지|출장지)\\s*(?:를|을)?\\s*([\\p{L}]{2,20}?)\\s*(?:으로|로)"
            + "|(?:목적지|출장지)\\s*(?:는|은)\\s*([\\p{L}]{2,20})\\s*$"
            // "부산으로 바꿔줘 / 변경해줘" — the change-to construction, marker and verb together.
            // Grammar again, not phrase-guessing: 로 marks the target of 바꾸다/변경. With two
            // destinations in the recent history the 14B mapper kept echoing the old one into one
            // field while writing the new one into another, and the draft went to 도쿄 titled
            // 부산 출장 — deterministic beats retuning the model here.
            + "|([\\p{L}]{2,20}?)\\s*(?:으로|로)\\s*(?:바꾸|바꿔|변경)"
            // English change-to construction: "change/set the city/destination to X" — the
            // same explicit marker+verb grammar, which the 14B mapper half-applies just like
            // the Korean one did (title said Shibuya, the destination field kept Osaka).
            + "|(?i:change|switch|set|update)\\s+(?i:the\\s+)?(?i:city|destination|place)\\s+(?i:to)\\s+([\\p{L}][\\p{L} ]{1,29})"
            // Bare ellipsis: "change to tokyo" — no noun says WHAT changes, so this form
            // only applies when the captured words resolve in the region master (see the
            // resolution gate below); "change to next week" stays a date edit.
            + "|(?i:change|switch)\\s+(?i:it\\s+)?(?i:to)\\s+([\\p{L}][\\p{L} ]{1,29})");

    /** Change-to targets that are TIMES, not places: "내일로 바꿔줘" moves the date, not the trip. */
    private static final java.util.regex.Pattern DEST_NOT_A_PLACE = java.util.regex.Pattern.compile(
            "^(?i)(내일|오늘|모레|어제|글피|다음\\s*주|다음\\s*달|이번\\s*주|이번\\s*달|담주|\\d+[일월주년]|tomorrow|today|yesterday|next\\s*(?:week|month)|this\\s*(?:week|month))$");

    /**
     * Apply a destination stated with its explicit marker, through the SAME writer path the
     * mapper's DESTINATION output takes — so the period selections refresh exactly as they would
     * have. Exists because the mapper misread "목적지를 도쿄로 바꿔줘" as an indefinite date change
     * and asked about dates while the destination silently stayed put.
     */
    private boolean backfillDestination(ArrayNode documents, ObjectNode state, String message,
                                        String bizplayToken) {
        if (documents.isEmpty() || message == null || message.isBlank()) {
            return false;
        }
        java.util.regex.Matcher m = DEST_PHRASE.matcher(message);
        if (!m.find()) {
            return false;
        }
        // BOTH bare change-to forms — Korean "X로 바꿔줘" (group 3) and English "change to X"
        // (group 5) — carry no field noun, so X must prove itself as a place: "제목을 파트너
        // 미팅으로 바꿔줘" grabbed 미팅 as the destination until the shadow verifier caught it.
        boolean bareChange = (m.group(3) != null || m.group(5) != null)
                && m.group(1) == null && m.group(2) == null && m.group(4) == null;
        String place = m.group(1) != null ? m.group(1)
                : m.group(2) != null ? m.group(2)
                : m.group(3) != null ? m.group(3)
                : m.group(4) != null ? m.group(4) : m.group(5);
        if (place != null) {
            place = place.trim();
            // 삿포로 + 로 marker = "삿포로로": the lazy capture stops at the first 로 and
            // yields 삿포. A 로-ending NAME always takes the bare 로 marker (vowel-final),
            // so "captured + 로로" appearing in the text means the 로 belongs to the name.
            if (message.contains(place + "로로")) {
                place = place + "로";
            }
        }
        String oldDest = state.path("destination").asText("");
        if (place == null || place.isBlank() || place.equals(oldDest)
                || DEST_NOT_A_PLACE.matcher(place.trim()).matches()) {
            return false;
        }
        // A copula ending rides along in the sentence-final capture: "목적지는 도쿄야" yields
        // 도쿄야. Trimming blindly would maim real names (나고야), so the trimmed form is only
        // a CANDIDATE — the region master, then the judge, decides which form the user meant.
        String stripped = place.replaceFirst("(?:이에요|예요|입니다|이야|야|요)$", "");
        if (stripped.length() < 2) {
            stripped = place;
        }
        // Every capture is checked against the region master first — a hit both proves X is a
        // place and canonicalizes it ("Osaka" -> 오사카, "도쿄야" -> 도쿄 when listed).
        JsonNode hit = destinationResolverAgentService.resolveDestinationText(
                documents, place, bizplayToken);
        if ((hit == null || hit.path("name").asText("").isBlank()) && !stripped.equals(place)) {
            hit = destinationResolverAgentService.resolveDestinationText(
                    documents, stripped, bizplayToken);
        }
        if (hit != null && !hit.path("name").asText("").isBlank()) {
            place = hit.path("name").asText("");
        } else if (bareChange) {
            // "change to X" without a noun: only a region-master hit proves X is a place.
            return false;   // not a known region — the mapper handles whatever it is
        } else {
            // Explicit-noun capture the master doesn't know. On a free-text form that can be a
            // genuine place ("목적지를 청주로") — but the grammar also swallows non-places
            // ("세부 목적지는 없어" captured 없어 and moved the trip) and copula endings
            // ("목적지는 도쿄야" captured 도쿄야, yet 나고야 is a real name). The model reads
            // the sentence and returns the bare place name — or nothing; the only code check
            // is deterministic (the answer must literally appear in the message).
            boolean ko = message.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
            JsonNode got = slotFillerAgentService.extract(message, java.util.Map.of(
                    "placeName", "the PLACE the user is naming as the trip's new destination, "
                            + "as the bare place name only — drop any Korean particle or copula "
                            + "ending that is not part of the name itself. OMIT this field when "
                            + "the message names no destination (a negation like 없어 = there "
                            + "is none, a decline, or talk about something else)"),
                    ko, java.util.List.<String>of());
            String judged = got.path("placeName").asText("").trim();
            if (judged.isBlank() || !message.contains(judged)) {
                log.info("Destination phrase capture '{}' vetoed by the judge ('{}').",
                        place, truncateForLog(message));
                return false;
            }
            place = judged;
        }
        writeDestination(documents, state, place);
        return true;
    }

    /** Write a destination everywhere it lives: the state slot OR the form's DESTINATION
     * field (via the shared writer), plus the composed-title rename when the title was ours. */
    private void writeDestination(ArrayNode documents, ObjectNode state, String place) {
        String oldDest = state.path("destination").asText("");
        String key = null;
        for (JsonNode f : state.path("fields")) {
            if (f.path("key").asText("").endsWith("DESTINATION")) {
                key = f.path("key").asText();
                break;
            }
        }
        if (key == null) {
            state.put("destination", place);
        } else {
            ObjectNode mapped = objectMapper.createObjectNode();
            mapped.put(key, place);
            formValueWriterService.apply((ObjectNode) documents.get(0), state.path("fields"), state, mapped);
        }
        // A title composed from the OLD destination is stale the moment the destination moves —
        // that is how a plan ended up titled 오사카 출장 while going to 도쿄. Only the composed
        // form is touched; a title the user wrote stays theirs.
        ObjectNode doc = (ObjectNode) documents.get(0);
        String title = doc.path("title").asText("");
        if (!oldDest.isBlank()
                && (title.equals(oldDest + " 출장") || title.equals(oldDest + " business trip"))) {
            String renamed = title.replace(oldDest, place);
            doc.put("title", renamed);
            state.put("composedTitle", renamed);   // still ours — keep the provenance current
        }
        log.info("Destination '{}' applied deterministically (was '{}').", place, oldDest);
    }

    /**
     * Is this message the user saying "go ahead and file it"? Decided by the slot-filler LLM with
     * the recent turns as context — not a phrase list, which is exactly what kept failing here:
     * every list missed the next phrasing ("create plan", "그럼 올려", "ㄱㄱ"), and each miss looked
     * like the agent ignoring the user. Only consulted once the form is COMPLETE, so a mid-fill
     * "만들어줘" can never file a half-built plan. Fails closed: unsure or unavailable → false,
     * and the turn falls through to the normal ready message.
     */
    /**
     * A stable fingerprint of everything a turn can EDIT: the draft document plus the slots that
     * live in state rather than the document (travellers, resolved ids, origin/destination).
     * Compared before/after a turn to decide whether the words changed anything — the invariant
     * that gates the submit judge.
     */
    /**
     * The draft minus its COMPOSED fields: title and content are re-phrased slightly by the
     * mapper on every turn ("도쿄로의 해외출장 계획입니다" -> "도쿄로의 장기 해외출장 계획") — that
     * drift is generation, not a user edit, and counting it kept the submit judge locked out
     * forever. Compared with JsonNode.equals (order-insensitive): the value writers also rebuild
     * objects with different KEY ORDER while every value stays identical.
     */
    /**
     * The transport the user names in words, normalized to the provider's transportType enum —
     * grammar/enum mapping, one of the allowed rule categories. Silent when nothing recognisable
     * is named, so ordinary sentences never disturb a chosen value.
     */
    private void backfillTransport(ObjectNode state, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        // Latin words need WORD BOUNDARIES: "bus" matched inside "business trip" (the chat
        // sends a context preamble that says exactly that), and every flight became a bus.
        String[][] map = {
                {"비행기|항공|플라이트|\bflight\b|\bairplane\b|\bair\b|\bfly\b|\bflying\b|\bflew\b", "PUBLIC_AIRLINE"},
                {"\bKTX\b|\bSRT\b|기차|열차|\btrain\b", "PUBLIC_TRAIN"},
                {"고속버스|버스|\bbus\b", "PUBLIC_BUS"},
                {"렌터카|렌트카|\brental\b", "RENTAL_CAR"},
                {"법인차|법인 ?차량", "CORP_CAR"},
                {"택시|\btaxi\b", "TAXI"},
                {"자차|자가용|내 ?차|\bmy car\b|\bdrive\b", "PRIVATE"},
                {"대중교통", "PUBLIC"},
        };
        for (String[] m : map) {
            if (java.util.regex.Pattern.compile("(?iu)(" + m[0] + ")").matcher(text).find()) {
                if (!m[1].equals(state.path("transportType").asText(""))) {
                    state.put("transportType", m[1]);
                    log.info("Transport type set to {} from the user's words.", m[1]);
                }
                return;
            }
        }
    }

    /** "This message says something date-like" — generous on purpose, mirrors the settlement guard. */
    private static final java.util.regex.Pattern DATEISH = java.util.regex.Pattern.compile(
            "(?iu)(\\d|오늘|어제|내일|모레|글피|지난|이번|다음|요일|하루|이틀"
            + "|today|tomorrow|yesterday|next|last|this"
            + "|mon|tue|wed|thu|fri|sat|sun"
            + "|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)");

    /**
     * The same restore for the BSTR_PERIOD issued item: on a dateless turn the writer rewrote
     * its selections to today ("2026-08-29 ~ 2026-08-29" on a 9/25-26 trip). When the rewritten
     * period STARTS today and the message named no date, the whole item is put back — dates and
     * region info alike.
     */
    private void restoreInventedPeriodItem(ObjectNode document, JsonNode periodBefore) {
        if (periodBefore == null) {
            return;
        }
        ArrayNode issuedItems = document.withArray("issuedItems");
        for (int i = 0; i < issuedItems.size(); i++) {
            JsonNode issued = issuedItems.get(i);
            if (!"BSTR_PERIOD".equals(issued.path("item").path("itemType").asText(""))) {
                continue;
            }
            if (issued.equals(periodBefore)) {
                log.info("period item untouched this turn");
                return;   // untouched
            }
            String newStart = issued.path("selections").path(0).path("selectionName").asText("");
            if (newStart.startsWith(java.time.LocalDate.now().toString())) {
                issuedItems.set(i, periodBefore.deepCopy());
                log.info("Restored the BSTR_PERIOD item — the mapper had rewritten the trip "
                        + "period to today on a dateless turn.");
            } else if (!periodEssentialsEqual(issued, periodBefore)) {
                log.info("period item REALLY changed: before={} after={}",
                        periodBefore.toString(), issued.toString());
            } else if (periodEssentialsEqual(issued, periodBefore)) {
                // The writer REBUILT the row — same dates, same region, different key order or
                // node types. Cosmetic churn, but it read as "the turn changed the draft" and
                // kept the submit judge locked out. Byte-stable beats freshly-serialized here.
                issuedItems.set(i, periodBefore.deepCopy());
            }
            return;
        }
    }

    /** Same VALUES in every selections row (dates, region, memos) — structure ignored. */
    private boolean periodEssentialsEqual(JsonNode a, JsonNode b) {
        JsonNode sa = a.path("selections");
        JsonNode sb = b.path("selections");
        if (sa.size() != sb.size()) {
            return false;
        }
        for (int i = 0; i < sa.size(); i++) {
            for (String k : new String[]{"selectionId", "selectionName", "selectionErpCode",
                    "selectionMemo", "selectionContent", "selectionAreaInfo"}) {
                if (!sa.get(i).path(k).asText("").equals(sb.get(i).path(k).asText(""))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Restore a document date the mapper just moved onto TODAY from a message naming no date. */
    private void restoreInventedToday(ObjectNode document, String key, String before) {
        String now = document.path(key).asText("");
        if (before.isBlank() || now.equals(before)
                || !now.startsWith(java.time.LocalDate.now().toString())) {
            return;
        }
        document.put(key, before);
        log.info("Restored {} to {} — the mapper had stamped today into it on a dateless turn.",
                key, before);
    }

    /**
     * Copy chat-captured slots the manual save payload has no field for. Fill-only: a value the
     * payload DID carry is never overwritten. Silent when the session is missing — a purely
     * manual save has no conversation to borrow from.
     */
    private void mergeChatCapturedState(ObjectNode state, String agentSessionId, String corpNo) {
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return;
        }
        try {
            BizplayPlanAgentRequest lookup = new BizplayPlanAgentRequest();
            lookup.setCorpNo(corpNo);
            lookup.setSessionId(agentSessionId);
            ConversationalAgentSession chat = resolveSession(lookup);
            ObjectNode chatState = loadState(chat);
            // routeNote included: the chat's completeness check accepts a held route note for
            // the 이동경로 field — dropping it here made the SAME form READY in chat and then
            // "required fields are missing: 이동경로" at save. The two paths must see one state.
            for (String k : new String[]{"transportType", "origin", "destinationDetail", "routeNote"}) {
                if (state.path(k).asText("").isBlank()) {
                    String v = chatState.path(k).asText("");
                    if (!v.isBlank()) {
                        state.put(k, v);
                        log.info("Chat-captured {} '{}' carried into the manual save.", k, v);
                    }
                }
            }
        } catch (Exception e) {
            log.info("No chat state to merge into the manual save: {}", e.getMessage());
        }
    }

    /**
     * Mid-session trip-type (classification) change. Two shapes:
     * (a) the target named or a chip clicked -> resolve it, and if it differs, WIPE the draft,
     *     stage a recap of the confirmed facts, and replay through the fresh-form path so the
     *     new form loads with everything the user already said re-applied;
     * (b) "I want to change the classification" without a target -> answer with the actual
     *     option CHIPS (never a bare "change it to what?" question).
     * Gated by form terminology / the chip token, then judged by the LLM - a sentence merely
     * containing the word "type" never switches anything.
     */
    private BizplayPlanAgentResponse maybeSwitchTripType(BizplayPlanAgentRequest request,
            ConversationalAgentSession session, ObjectNode state, ArrayNode documents,
            String message, String turnText, String bizplayToken, boolean ko) {
        if (documents.isEmpty() || message == null || message.isBlank()) {
            return null;
        }
        String bare = stripChatContext(message).trim();
        String lower = bare.toLowerCase(java.util.Locale.ROOT);
        boolean chip = lower.startsWith("trip type:");
        JsonNode catalog = bizplayGatewayService.getPurposeCatalog(request.getCorpUserId(), bizplayToken);
        List<PurposeOption> options = purposeSegmentAgentService.flattenCatalog(catalog);
        // The cheap gate is DATA, not vocabulary: does the message name one of the corp's own
        // form labels (\ud574\uc678\ucd9c\uc7a5, \uc7a5\uae30, \uc77c\ubc18 \u2026)? A word list for "classification" missed \ubd84\ub958 \u2014
        // the very word this UI prints \u2014 and the switch silently never happened. Whether the
        // user actually wants the change is then judged by the LLM below, never by these words.
        boolean namesAForm = false;
        for (PurposeOption o : options) {
            for (String part : String.valueOf(o.getLabel()).split("[^\uac00-\ud7a3A-Za-z0-9]+")) {
                if (part.length() >= 2 && bare.contains(part)) {
                    namesAForm = true;
                    break;
                }
            }
            if (namesAForm) {
                break;
            }
        }
        // The data gate only sees a message that NAMES a form. "change trip classification
        // type" names none - and asking in plain words is exactly how a user asks. So when the
        // data gate misses, the JUDGE decides; the words themselves are never the test.
        // (Our own chip tokens - "approver:30192" and friends - are wire format, not a person
        // talking, so they skip the judge rather than paying for an LLM call.)
        boolean judgedWantsChange = false;
        if (!chip && !namesAForm) {
            if (answersPendingAsk(session, state, bare, ko)) {
                return null;   // it answers the question on screen — not a form change
            }
            if (bare.matches("(?s)^[A-Za-z_-]+:\\S.*")) {
                return null;
            }
            // Asked as a CHOICE, not as a yes/no: "do they want a type change?" answered yes to
            // every "change X" sentence, so a title edit opened the trip-type chips. Naming the
            // alternative the user actually means makes the judgement discriminating.
            JsonNode asks = slotFillerAgentService.extract(bare, java.util.Map.of(
                    "changeTarget", "What does the user want to change? Answer with exactly one "
                            + "word. \"form\" - they want this plan moved to a DIFFERENT trip-plan "
                            + "form / type / classification. \"field\" - they want to change "
                            + "something written ON the current form (title, dates, destination, "
                            + "travellers, purpose text, transport, amounts, ...). \"none\" - they "
                            + "are not asking to change anything"),
                    ko, recentTurns(session));
            if (!"form".equalsIgnoreCase(asks.path("changeTarget").asText(""))) {
                return null;
            }
            judgedWantsChange = true;
        }
        long curPurposeId = documents.get(0).path("bstrPurposeId").asLong(0);
        Long curSegmentId = documents.get(0).hasNonNull("bstrSegmentId")
                ? documents.get(0).path("bstrSegmentId").asLong() : null;
        PurposeResolutionResult res = purposeSegmentAgentService.resolve(bare, options);
        PurposeOption resolved = res.isResolved() ? res.getResolved() : null;
        if (resolved == null) {
            // "분류를 장기로 바꿔줘" names only the SEGMENT — the purpose is the one already on
            // the draft. Match the word against this purpose's own segment labels: exactly one
            // hit is unambiguous, so there is nothing to ask about.
            PurposeOption hit = null;
            int hits = 0;
            for (PurposeOption o : options) {
                if (o.getPurposeId() != curPurposeId || o.getSegmentId() == null) {
                    continue;
                }
                String seg = String.valueOf(o.getLabel());
                seg = seg.contains("·") ? seg.substring(seg.lastIndexOf('·') + 1).trim() : seg;
                if (seg.length() >= 2 && bare.contains(seg)) {
                    hit = o;
                    hits++;
                }
            }
            if (hits == 1) {
                resolved = hit;
                log.info("Trip-type switch: segment '{}' matched within the current purpose.",
                        hit.getLabel());
            }
        }
        if (resolved != null) {
            PurposeOption chosen = resolved;
            boolean different = chosen.getPurposeId() != curPurposeId
                    || (chosen.getSegmentId() == null ? curSegmentId != null
                            : !chosen.getSegmentId().equals(curSegmentId));
            if (!different) {
                return null;   // same form named - nothing to switch
            }
            // Naming a form is not the same as asking to move to it ("장기 출장이라 힘들었어").
            // A chip IS the request; typed words are judged — the same "what did they mean?"
            // question the rest of the flow asks, never a phrase list.
            if (!chip) {
                JsonNode wants = slotFillerAgentService.extract(bare, java.util.Map.of(
                        "wantsTypeChange", "\"yes\" ONLY if the user is asking to CHANGE this "
                                + "trip plan's form — its purpose, classification or type — to a "
                                + "different one. Merely mentioning a trip kind in passing is not"),
                        ko, recentTurns(session));
                if (!"yes".equalsIgnoreCase(wants.path("wantsTypeChange").asText(""))) {
                    return null;
                }
            }
            // Recap the confirmed facts, wipe the paper-specific state, replay as a fresh load.
            String recap = recapFacts(documents, state, ko);
            state.remove("fields");
            state.remove("pendingAsk");
            state.withArray("staged").add(recap);
            session.setDraftJson(objectMapper.createArrayNode());
            saveState(session, state);
            sessionRepo.save(session);
            log.info("Trip-type switch: -> {} (facts recapped: {})", chosen.getLabel(),
                    truncateForLog(recap));
            BizplayPlanAgentRequest replay = new BizplayPlanAgentRequest();
            replay.setCorpNo(request.getCorpNo());
            replay.setCorpUserId(request.getCorpUserId());
            replay.setSessionId(session.getId().toString());
            // The replay is a fresh turn, so it must carry the client's context preamble too -
            // it is what says which language to answer in. Without it a switch made in an
            // English chat came back in Korean, because the chip's own text is Korean.
            java.util.regex.Matcher pre = java.util.regex.Pattern.compile(
                    "(?s)^\\(Current user:.*?no (?:Korean|English)\\.\\)\\s*").matcher(message);
            String preamble = pre.find() ? pre.group() : "";
            replay.setMessage(preamble + chosen.getSendText());
            return chat(replay, bizplayToken);
        }
        if (chip) {
            return null;
        }
        // Terminology without a resolvable target: does the user actually want a type change?
        // (Already answered above when the data gate missed - no second call for one question.)
        if (!judgedWantsChange) {
            JsonNode got = slotFillerAgentService.extract(bare, java.util.Map.of(
                    "wantsTypeChange", "\"yes\" ONLY if the user asks to change this trip plan's "
                            + "type / classification / form"), ko, recentTurns(session));
            if (!"yes".equalsIgnoreCase(got.path("wantsTypeChange").asText(""))) {
                return null;
            }
        }
        List<PurposeOption> ofPurpose = new ArrayList<>();
        for (PurposeOption o : options) {
            if (o.getPurposeId() == curPurposeId) {
                ofPurpose.add(o);
            }
        }
        List<PurposeOption> offer = ofPurpose.size() > 1 ? ofPurpose : options;
        String ask = t(ko, "Sure - which trip type should this become? ",
                "\ub124 - \uc5b4\ub5a4 \ucd9c\uc7a5 \uc720\ud615\uc73c\ub85c \ubc14\uafc0\uae4c\uc694? ");
        appendTurn(session, "user", message);
        appendTurn(session, "assistant", ask);
        saveState(session, state);
        ConversationalAgentSession saved = sessionRepo.save(session);
        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent("SEGMENT_SELECTION")
                .subAgents(List.of("PURPOSE_SEGMENT_AGENT", "SLOT_FILLER_AGENT"))
                .reply(ask)
                .pendingChoices(List.of(segmentChoice(offer)))
                .travelers(travelerNames(state))
                .travelerIds(travelerIdList(state))
                .destination(state.path("destination").asText(null))
                .destinationCountry(state.path("destinationCountry").asText(null))
                .draftJson(saved.getDraftJson())
                .build();
    }

    /** The confirmed facts as one staged sentence, so a form switch re-applies them. */
    private String recapFacts(ArrayNode documents, ObjectNode state, boolean ko) {
        ObjectNode d = (ObjectNode) documents.get(0);
        StringBuilder sb = new StringBuilder(ko ? "\uc774\ubbf8 \ud655\uc815\ub41c \ub0b4\uc6a9: "
                : "Facts already confirmed: ");
        String start = d.path("bstrStartDate").asText("");
        String end = d.path("bstrEndDate").asText("");
        if (!start.isBlank()) {
            sb.append(start).append(" ~ ").append(end).append(". ");
        }
        String dest = state.path("destination").asText("");
        if (!dest.isBlank()) {
            sb.append(ko ? "\ubaa9\uc801\uc9c0: " : "Destination: ").append(dest).append(". ");
        }
        List<String> names = travelerNames(state);
        if (names != null && !names.isEmpty()) {
            sb.append(ko ? "\ucd9c\uc7a5\uc790: " : "Travelers: ")
              .append(String.join(", ", names)).append(". ");
        }
        String title = d.path("title").asText("");
        if (!title.isBlank()) {
            sb.append(ko ? "\uc81c\ubaa9: " : "Title: ").append(title).append(". ");
        }
        return sb.toString().trim();
    }

    /** state.periodPlaces as a date-sorted array for clients: [{date, place, country, detail}]. */
    private ArrayNode periodPlacesForResponse(ObjectNode state) {
        JsonNode pp = state.path("periodPlaces");
        if (!pp.isObject() || pp.isEmpty()) {
            return null;
        }
        java.util.List<String> dates = new ArrayList<>();
        pp.fieldNames().forEachRemaining(dates::add);
        java.util.Collections.sort(dates);
        ArrayNode out = objectMapper.createArrayNode();
        for (String d : dates) {
            ObjectNode row = out.addObject();
            row.put("date", d);
            row.put("place", pp.path(d).path("place").asText(""));
            row.put("country", pp.path(d).path("country").asText(""));
            row.put("detail", pp.path(d).path("detail").asText(""));
        }
        return out;
    }

    /**
     * Deterministic relative-date edit: (시작일|종료일|whole trip) x (늦춰/당겨/연장/단축,
     * postpone/advance/extend/shorten) x (하루/이틀/N일/N days/일주일) plus the absolute words
     * 오늘/내일/모레/글피/today/tomorrow. Anything outside this grammar stays with the mapper;
     * arithmetic uses the BEFORE-turn dates so it cannot compound with a mapper write.
     */
    private boolean backfillDateEdit(ArrayNode documents, JsonNode beforeDoc, StringBuilder reply,
                                     String text, boolean ko) {
        if (documents.isEmpty() || text == null || text.isBlank()) {
            return false;
        }
        String s0 = beforeDoc.path("bstrStartDate").asText("");
        String e0 = beforeDoc.path("bstrEndDate").asText("");
        if (s0.length() < 10) {
            return false;   // no period yet — nothing to shift
        }
        java.time.LocalDate start = java.time.LocalDate.parse(s0.substring(0, 10));
        java.time.LocalDate end = e0.length() >= 10
                ? java.time.LocalDate.parse(e0.substring(0, 10)) : start;
        boolean hasStart = text.matches("(?s).*(시작일|출발일|start\\s?date).*");
        boolean hasEnd = text.matches("(?s).*(종료일|복귀일|도착일|end\\s?date).*");
        int dir = 0;
        boolean extend = false;
        boolean shorten = false;
        if (text.matches("(?si).*(늦춰|늦추|미뤄|미루|연기|postpone|delay"
                + "|(?:push|move)(?:\\s+\\w+){0,3}\\s+back).*")) {
            dir = 1;
        } else if (text.matches("(?si).*(당겨|당기|앞당|advance|move\\s*up|bring\\s*forward).*")) {
            dir = -1;
        } else if (text.matches("(?si).*(늘려|늘리|연장|extend|lengthen).*")) {
            extend = true;
        } else if (text.matches("(?si).*(줄여|줄이|단축|shorten).*")) {
            shorten = true;
        }
        Integer n = null;
        java.util.regex.Matcher am = java.util.regex.Pattern.compile(
                "(?iu)(하루|이틀|사흘|나흘|일주일|한\\s*주|(\\d+)\\s*일(?!차)|(\\d+)\\s*주"
                + "|a\\s+day|one\\s+day|(\\d+)\\s*days?|a\\s+week)").matcher(text);
        if (am.find()) {
            String g = am.group(1);
            if (g.equals("하루") || g.matches("(?i)a\\s+day|one\\s+day")) {
                n = 1;
            } else if (g.equals("이틀")) {
                n = 2;
            } else if (g.equals("사흘")) {
                n = 3;
            } else if (g.equals("나흘")) {
                n = 4;
            } else if (g.equals("일주일") || g.matches("한\\s*주") || g.matches("(?i)a\\s+week")) {
                n = 7;
            } else if (am.group(2) != null) {
                n = Integer.parseInt(am.group(2));
            } else if (am.group(3) != null) {
                n = Integer.parseInt(am.group(3)) * 7;
            } else if (am.group(4) != null) {
                n = Integer.parseInt(am.group(4));
            }
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate abs = null;
        if (text.contains("글피")) {
            abs = today.plusDays(3);
        } else if (text.contains("모레")) {
            abs = today.plusDays(2);
        } else if (text.contains("내일") || text.matches("(?si).*\\btomorrow\\b.*")) {
            abs = today.plusDays(1);
        } else if (text.contains("오늘") || text.matches("(?si).*\\btoday\\b.*")) {
            abs = today;
        } else {
            // Explicit "M월 D일" target: pure calendar arithmetic — "출발일을 9월 25일로
            // 바꿔줘" was falling through to the mapper, which ignored it entirely.
            java.util.regex.Matcher md = java.util.regex.Pattern.compile(
                    "(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일").matcher(text);
            if (md.find()) {
                int mo = Integer.parseInt(md.group(1));
                int da = Integer.parseInt(md.group(2));
                if (mo >= 1 && mo <= 12 && da >= 1 && da <= 31) {
                    int yr = mo < today.getMonthValue() - 6 ? today.getYear() + 1 : today.getYear();
                    try {
                        abs = java.time.LocalDate.of(yr, mo, da);
                    } catch (java.time.DateTimeException ignored) {
                        // day out of range for the month — the mapper takes it
                    }
                }
            }
        }
        java.time.LocalDate ns = start;
        java.time.LocalDate ne = end;
        if (abs != null && (dir != 0 || text.matches("(?s).*(로|으로)\\s*(바꾸|바꿔|변경|옮겨|이동|해|set|change|move).*"))) {
            if (hasStart && !hasEnd) {
                ns = abs;
                if (ne.isBefore(ns)) {
                    ne = ns;
                }
            } else if (hasEnd && !hasStart) {
                ne = abs;
                if (ne.isBefore(ns)) {
                    ns = ne;
                }
            } else {
                long delta = java.time.temporal.ChronoUnit.DAYS.between(start, abs);
                ns = abs;
                ne = end.plusDays(delta);
            }
        } else if (n != null && dir != 0) {
            if (hasStart && !hasEnd) {
                ns = start.plusDays((long) dir * n);
                if (ne.isBefore(ns)) {
                    ne = ns;
                }
            } else if (hasEnd && !hasStart) {
                ne = end.plusDays((long) dir * n);
                if (ne.isBefore(ns)) {
                    ns = ne;
                }
            } else {
                ns = start.plusDays((long) dir * n);
                ne = end.plusDays((long) dir * n);
            }
        } else if (n != null && extend) {
            ne = end.plusDays(n);
        } else if (n != null && shorten) {
            ne = end.minusDays(n);
            if (ne.isBefore(ns)) {
                ne = ns;
            }
        } else {
            return false;   // outside the grammar — the mapper (and the verifier) take it
        }
        if (ns.equals(start) && ne.equals(end)) {
            return false;
        }
        ObjectNode doc = (ObjectNode) documents.get(0);
        doc.put("bstrStartDate", ns.toString());
        doc.put("bstrEndDate", ne.toString());
        // Keep the single chat-shape period row in step (per-day rows are rebuilt at save).
        for (JsonNode issued : doc.withArray("issuedItems")) {
            if ("BSTR_PERIOD".equals(issued.path("item").path("itemType").asText(""))
                    && issued.path("selections").size() == 1) {
                ObjectNode row = (ObjectNode) issued.path("selections").get(0);
                row.put("selectionName", ns.toString());
                row.put("selectionErpCode", ne.toString());
            }
        }
        reply.append(t(ko, "Dates updated: " + ns + " ~ " + ne + ". ",
                "출장 기간을 " + ns + " ~ " + ne + "(으)로 바꿨어요. "));
        log.info("Date edit applied deterministically: '{}' -> {} ~ {}",
                truncateForLog(text), ns, ne);
        return true;
    }

    /** Day-words that name a period row without a digit ("첫날", "마지막 날", "2일차"). */
    private static final java.util.regex.Pattern PERIOD_ORDINAL = java.util.regex.Pattern.compile(
            "(?iu)(첫\\s?째?\\s?날|둘\\s?째|셋\\s?째|넷\\s?째|다섯\\s?째|마지막\\s?날"
            + "|\\d+\\s?일\\s?차|day\\s?\\d|first\\s?day|last\\s?day|second\\s?day)");

    /**
     * Capture "day X goes to place Y" assignments into {@code state.periodPlaces}
     * (date → {place, country, detail}). Cheap gates first (a day-word AND a resolvable
     * region in the text), then ONE focused extraction; every extracted place must resolve
     * against the paper's region lists or it is reported, never silently stored.
     */
    private void capturePeriodDestinations(ArrayNode documents, ObjectNode state, String turnText,
                                           List<String> subAgents, StringBuilder reply,
                                           boolean ko, String token) {
        if (documents.isEmpty() || turnText == null || turnText.isBlank()) {
            return;
        }
        String text = stripChatContext(turnText);
        if (!DATEISH.matcher(text).find() && !PERIOD_ORDINAL.matcher(text).find()) {
            return;
        }
        if (destinationResolverAgentService.resolveDestinationText(documents, text, token) == null) {
            return;   // no known region named — nothing to assign
        }
        ObjectNode doc = (ObjectNode) documents.get(0);
        String startD = doc.path("bstrStartDate").asText("");
        String endD = doc.path("bstrEndDate").asText("");
        JsonNode got = slotFillerAgentService.extract(text, java.util.Map.of(
                "dayDestinations", "ONLY when the user assigns destinations to SPECIFIC days "
                        + "of the trip (the trip runs " + startD + " to " + endD + "; '첫날' = "
                        + startD + ", '마지막 날' = " + endD + "): a JSON array like "
                        + "[{\"date\":\"yyyy-MM-dd\",\"place\":\"city name\","
                        + "\"detail\":\"optional short venue note\"}] with one entry per "
                        + "day mentioned. OMIT entirely when the message names one destination "
                        + "for the whole trip or no day-place pairing"), ko, java.util.List.<String>of());
        JsonNode dd = got.path("dayDestinations");
        if (dd.isTextual()) {
            try {
                dd = objectMapper.readTree(dd.asText());
            } catch (Exception e) {
                return;
            }
        }
        if (!dd.isArray() || dd.isEmpty()) {
            return;
        }
        ObjectNode places = state.withObject("periodPlaces");
        String firstDate = null;
        for (JsonNode e : dd) {
            String date = e.path("date").asText("");
            String place = e.path("place").asText("").trim();
            if (!date.matches("\\d{4}-\\d{2}-\\d{2}") || place.isBlank()) {
                continue;
            }
            JsonNode r = destinationResolverAgentService.resolveDestinationText(documents, place, token);
            if (r == null || r.path("name").asText("").isBlank()) {
                reply.append(t(ko, "I couldn't match \"" + place + "\" to a region for "
                                + date + " — that day keeps the main destination. ",
                        "\"" + place + "\"을(를) " + date + "의 지역 목록에서 찾지 못해 그 날은 "
                                + "기본 목적지로 두었어요. "));
                continue;
            }
            ObjectNode entry = places.withObject(date);
            entry.put("place", r.path("name").asText(""));
            entry.put("country", r.path("countryName").asText(""));
            if (!e.path("detail").asText("").isBlank()) {
                entry.put("detail", e.path("detail").asText(""));
            }
            if (firstDate == null || date.compareTo(firstDate) < 0) {
                firstDate = date;
            }
            reply.append(t(ko, date + " → " + r.path("name").asText("") + ". ",
                    date + " → " + r.path("name").asText("") + ". "));
            log.info("Per-day destination: {} -> {} ({})", date,
                    r.path("name").asText(""), r.path("countryName").asText(""));
        }
        // The trip-wide slots follow the FIRST day so readiness/title stay coherent.
        if (firstDate != null) {
            JsonNode first = places.path(firstDate);
            writeDestination(documents, state, first.path("place").asText(""));
            if (!first.path("country").asText("").isBlank()) {
                state.put("destinationCountry", first.path("country").asText(""));
            }
            subAgents.add("SLOT_FILLER_AGENT");
        }
    }

    @Override
    public void noteTurn(String sessionId, String corpNo, String userText, String assistantText) {
        try {
            BizplayPlanAgentRequest lookup = new BizplayPlanAgentRequest();
            lookup.setCorpNo(corpNo);
            lookup.setSessionId(sessionId);
            ConversationalAgentSession session = resolveSession(lookup);
            if (userText != null && !userText.isBlank()) {
                appendTurn(session, "user", userText.trim());
            }
            if (assistantText != null && !assistantText.isBlank()) {
                appendTurn(session, "assistant", assistantText.trim());
            }
            sessionRepo.save(session);
            log.info("[NOTE] UI-handled turn recorded: '{}' -> '{}'",
                    truncateForLog(userText), truncateForLog(assistantText));
        } catch (Exception e) {
            log.info("[NOTE] could not record UI turn: {}", e.getMessage());
        }
    }

    @Override
    public JsonNode approvalIntent(String corpNo, JsonNode body) {
        String message = body.path("message").asText("");
        String pendingRolePerson = body.path("pendingRolePerson").asText("");
        String lines = body.path("lines").asText("");
        boolean awaitingSave = body.path("awaitingSaveConfirm").asBoolean(false);
        java.util.List<String> people = new ArrayList<>();
        for (JsonNode n : body.path("people")) {
            people.add(n.asText(""));
        }
        boolean ko = message.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
        StringBuilder situation = new StringBuilder(
                "Situation: the assistant is building the plan's APPROVAL LINE with the user. ");
        if (!pendingRolePerson.isBlank()) {
            situation.append("It just asked which ROLE ").append(pendingRolePerson)
                    .append(" should have (결재=APPROVAL, 합의=AGREE, ")
                    .append("수신=ACCEPT, 참조=REFERENCE). ");
        } else if (awaitingSave) {
            situation.append("It just asked whether to SAVE the plan to BizPlay now. ");
        } else {
            situation.append("It asked who should be in the approval line");
            if (!lines.isBlank()) {
                situation.append(" (already picked: ").append(lines).append(")");
            }
            situation.append(". ");
        }
        if (!people.isEmpty()) {
            situation.append("Selectable people: ")
                    .append(String.join(", ", people.subList(0, Math.min(people.size(), 15))))
                    .append(". The user may write a name in English romanization "
                            + "('kim do ha' means 김도하) — always answer with the EXACT name "
                            + "as it appears in the selectable list. ");
        }
        JsonNode got = slotFillerAgentService.extract(message, java.util.Map.of(
                "action", situation + "Judge what the user's message MEANS and answer EXACTLY one "
                        + "of: assign_line (names ONE or more people TOGETHER WITH their role - "
                        + "one pair is still an assignment: '김도하 as approval', 'A as approver', "
                        + "'김도하는 결재' - and so is a list of them: 'A as approver and B as "
                        + "agree' or '김도하는 결재, 김철수는 합의'), "
                        + "pick_person (names someone to add, no role stated), assign_role "
                        + "(answers the role question), no_more (no one else to add / finished "
                        + "picking), save_now "
                        + "(confirms saving, submitting, creating or filing the plan — a plan "
                        + "draft is on screen, so a bare command like 'create', 'save', '등록', "
                        + "'생성' refers to filing THIS plan), not_yet (declines "
                        + "saving for now), remove_person (wants someone taken out), other "
                        + "(an edit or anything else). The approval line is about who SIGNS the "
                        + "document. A message about the TRIP ITSELF - who TRAVELS on it "
                        + "(출장자, traveller), its dates, title, destination or travel route - "
                        + "is an edit for the form, so it is \"other\" even when it names one of "
                        + "the selectable people (\"출장자에 김도하도 추가해줘\" adds a "
                        + "TRAVELLER, it does not pick an approver)",
                "person", "ONLY for pick_person/remove_person: the person's NAME copied exactly "
                        + "from the selectable people list - and only when they are being put "
                        + "IN (or taken OUT OF) the APPROVAL LINE, never when they are named as "
                        + "someone who travels",
                "subject", "Which of these two is the message about? Answer exactly one word. "
                        + "\"approval\" - the APPROVAL LINE, i.e. who SIGNS this document "
                        + "(picking people for it, giving them 결재/합의/수신/참조, taking "
                        + "someone out of it, finishing it, saving the plan). \"trip\" - the TRIP "
                        + "ITSELF: who TRAVELS on it (출장자), its dates, title, purpose, "
                        + "destination, travel route or any other form field",
                "role", "ONLY for assign_role: APPROVAL, AGREE, ACCEPT or REFERENCE",
                "assignments", "ONLY for assign_line: every person=role pair, ';'-separated, "
                        + "e.g. '김도하=APPROVAL; 김철수=AGREE' — names EXACTLY from the "
                        + "selectable list, roles from APPROVAL/AGREE/ACCEPT/REFERENCE "
                        + "(approver/결재→APPROVAL, agree/합의→AGREE, 수신→ACCEPT, "
                        + "reference/참조→REFERENCE)"),
                ko, java.util.List.<String>of());
        log.info("[APPR] raw verdict: {}", got);
        String action = got.path("action").asText("").trim().toLowerCase(java.util.Locale.ROOT);
        // The judge sometimes answers with the DATA and leaves the label out - "김도하 as
        // approval" came back as {assignments: "김도하=APPROVAL"} with no action, so the
        // verdict fell through to "other", the client handed the turn to the plan agent, and a
        // sentence about the APPROVAL LINE was read there as "take 김도하 off the trip".
        // A filled slot IS the answer: read the action from what the judge actually returned.
        if (action.isBlank() || "other".equals(action)) {
            if (!got.path("assignments").asText("").isBlank()) {
                action = "assign_line";
            } else if (!pendingRolePerson.isBlank() && !got.path("role").asText("").isBlank()) {
                action = "assign_role";
            } else if (!got.path("person").asText("").isBlank()) {
                action = "pick_person";
            }
            if (!action.isBlank() && !"other".equals(action)) {
                log.info("[APPR] action read from the filled slot -> {}", action);
            }
        }
        // Two axes, judged separately: WHAT the message asks for, and WHICH of the two things
        // it is about. A person's name alone cannot make a sentence an approval-line pick -
        // "출장자에 김도하도 추가해줘" is about who TRAVELS, and belongs to the plan agent.
        // Prompt wording alone kept losing this; making the subject its own answer settles it.
        if ("trip".equalsIgnoreCase(got.path("subject").asText("").trim())
                && !"save_now".equals(action) && !"not_yet".equals(action)) {
            ObjectNode edit = objectMapper.createObjectNode();
            edit.put("action", "other");
            log.info("[APPR] '{}' is about the TRIP, not the approval line -> other",
                    truncateForLog(message));
            return edit;
        }
        ObjectNode out = objectMapper.createObjectNode();
        if (!java.util.Set.of("assign_line", "pick_person", "assign_role", "no_more", "save_now",
                "not_yet", "remove_person").contains(action)) {
            out.put("action", "other");
            log.info("[APPR] intent judged: other ('{}')", truncateForLog(message));
            return out;
        }
        if ("assign_line".equals(action)) {
            // Compound picks ("A as approver and B as agree"): validate every pair against the
            // roster and the role enum; anything that doesn't validate is dropped, and an empty
            // result falls back to a plain pick so the UI can at least ask the role.
            com.fasterxml.jackson.databind.node.ArrayNode pairs = objectMapper.createArrayNode();
            for (String pair : got.path("assignments").asText("").split(";")) {
                String[] kv = pair.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                String name = kv[0].trim();
                String r = kv[1].trim().toUpperCase(java.util.Locale.ROOT);
                if (!java.util.Set.of("APPROVAL", "AGREE", "ACCEPT", "REFERENCE").contains(r)) {
                    continue;
                }
                for (String cand : people) {
                    if (cand.equalsIgnoreCase(name) || cand.contains(name) || name.contains(cand)) {
                        ObjectNode p = pairs.addObject();
                        p.put("person", cand);
                        p.put("role", r);
                        break;
                    }
                }
            }
            if (pairs.isEmpty()) {
                action = "pick_person";
                for (String pair : got.path("assignments").asText("").split("[;=]")) {
                    String name = pair.trim();
                    for (String cand : people) {
                        if (!name.isBlank() && (cand.equalsIgnoreCase(name)
                                || cand.contains(name) || name.contains(cand))) {
                            out.put("person", cand);
                            break;
                        }
                    }
                    if (out.has("person")) {
                        break;
                    }
                }
            } else {
                out.put("action", "assign_line");
                out.set("assignments", pairs);
                log.info("[APPR] intent judged: assign_line {} ('{}')", pairs,
                        truncateForLog(message));
                return out;
            }
        }
        out.put("action", action);
        String person = got.path("person").asText("").trim();
        if (person.isBlank() && "remove_person".equals(action)) {
            // The model sometimes names the removal target only in the message — recover it
            // from the CURRENT LINE (that is where removal targets live, not the roster).
            for (String part : lines.split(",")) {
                String name = part.trim().replaceAll("^[A-Z_]+\s+", "");
                if (!name.isBlank() && message.contains(name)) {
                    person = name;
                    break;
                }
            }
        }
        if (!person.isBlank()) {
            boolean matched = false;
            for (String cand : people) {
                if (cand.equalsIgnoreCase(person) || cand.contains(person) || person.contains(cand)) {
                    out.put("person", cand);   // validated against the roster — never invented
                    matched = true;
                    break;
                }
            }
            if (!matched && lines != null && lines.contains(person)) {
                out.put("person", person);     // a removal target lives in the line itself
            }
        }
        String role = got.path("role").asText("").trim().toUpperCase(java.util.Locale.ROOT);
        if (java.util.Set.of("APPROVAL", "AGREE", "ACCEPT", "REFERENCE").contains(role)) {
            out.put("role", role);
        }
        log.info("[APPR] intent judged: {} person={} role={} ('{}')", action,
                out.path("person").asText("-"), out.path("role").asText("-"),
                truncateForLog(message));
        return out;
    }

    /**
     * A turn that stated a travel ROUTE names the company's registered SITES. The field mapper,
     * which ran earlier in this same turn, sees place names and offers one as the trip's
     * destination — turning an Osaka trip into one bound for an office in 부산, which then fails
     * the form's region check. When the destination it wrote is one of the route's own points,
     * what stood before the turn is put back.
     */
    private void restoreDestinationAfterRoute(ArrayNode documents, ObjectNode state,
                                              String destAtTurnStart, JsonNode route) {
        String now = state.path("destination").asText("");
        if (destAtTurnStart.isBlank() || now.isBlank() || now.equals(destAtTurnStart)) {
            return;
        }
        boolean isRoutePoint = routeMentions(route, now);
        if (!isRoutePoint) {
            return;   // a genuine destination change that happened to share the turn
        }
        state.put("destination", destAtTurnStart);
        if (!documents.isEmpty() && documents.get(0).isObject()) {
            formValueWriterService.refreshPeriod((ObjectNode) documents.get(0), state);
        }
        log.info("Destination restored to '{}' — '{}' is a stop on the route just stated, "
                + "not the trip's destination.", destAtTurnStart, now);
    }

    /** Is this text one of the route's own stops (either way round, ignoring the (본사) suffix)? */
    private boolean routeMentions(JsonNode route, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (JsonNode p : route.path("points")) {
            String point = p.asText("");
            if (!point.isBlank()
                    && (point.equals(text) || point.contains(text) || text.contains(point))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which traveller the user is asking to take OFF this plan — judged by the LLM from their
     * own words, in any phrasing or language. The cheap gate is DATA: the message must name
     * someone who is actually on the list, and the name the model answers with must be one of
     * them AND written in the message. Null when nobody is being removed.
     */
    private String travellerToRemove(String message, ObjectNode state, boolean ko,
                                     java.util.List<String> turns, String token) {
        List<String> names = travelerNames(state);
        if (names == null || names.isEmpty() || message == null || message.isBlank()) {
            return null;
        }
        boolean mentionsSomeone = names.stream().anyMatch(n -> !n.isBlank() && message.contains(n));
        if (!mentionsSomeone) {
            return null;
        }
        // "김도하 goes to 티엑스알로보틱스(부산) instead" names a person AND a route — it MOVES
        // them, it does not remove them. A message carrying two or more of the company's
        // registered destinations is a route statement, so the removal judge never sees it.
        int sites = 0;
        for (JsonNode o : planEnrichmentService.routeOptions(token)) {
            String site = o.path("name").asText("");
            if (!site.isBlank() && message.contains(site)) {
                sites++;
            }
        }
        if (sites >= 2) {
            return null;
        }
        // A message that BRINGS new facts — dates, a period — is filling the form, not emptying
        // the traveller list. "9월 21일부터 22일까지 오사카로 다녀올게. 출장자는 김충북" ASSIGNS
        // 김충북; reading it as a removal took the only traveller off the plan.
        if (DATEISH.matcher(message).find()) {
            return null;
        }
        try {
            // Asked as a CHOICE, not "is this a removal?": a single yes/no slot answered with
            // the person's name for any sentence that merely NAMED them, so "김도하 as
            // approval" - a line about who SIGNS the document - took them off the trip. Naming
            // the alternatives is what makes the judgement discriminating.
            JsonNode verdict = slotFillerAgentService.extract(message, java.util.Map.of(
                    "personAction", "The people on this trip are: " + String.join(", ", names)
                            + ". What does the message ask to do with the person it names? "
                            + "Answer with exactly one word. \"remove\" - take them OFF the trip "
                            + "(remove, drop, exclude, 빼다, 제외, 삭제). \"approval\" - give them "
                            + "a place in the APPROVAL LINE (as approver / 결재 / 합의 / 수신 / "
                            + "참조), which is about who SIGNS the document. \"route\" - say "
                            + "where that person travels. \"add\" - put them ON the trip "
                            + "(\"출장자는 김충북\", \"김도하도 추가해줘\"). \"other\" - anything "
                            + "else, including questions and edits to other fields",
                    "removeTraveller", "ONLY when personAction is \"remove\": that person's name "
                            + "copied exactly from the list"),
                    ko, turns);
            String act = verdict.path("personAction").asText("").trim()
                    .toLowerCase(java.util.Locale.ROOT);
            if (!"remove".equals(act)) {
                log.info("Traveller-removal judge: '{}' -> {} (nobody removed)",
                        truncateForLog(message), act.isBlank() ? "(no verdict)" : act);
                return null;
            }
            String got = verdict.path("removeTraveller").asText("").trim();
            for (String cand : names) {
                if (!cand.isBlank() && (cand.equalsIgnoreCase(got) || cand.contains(got)
                        || (!got.isBlank() && got.contains(cand))) && message.contains(cand)) {
                    return cand;
                }
            }
        } catch (Exception e) {
            log.info("Traveller-removal judge unavailable: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Take a traveller off the plan: their name, their id, and any route that was theirs alone.
     * The document fan-out is rebuilt from what remains — one document per traveller — and the
     * routes with it, so the preview the user sees next is the plan as it now stands.
     */
    private BizplayPlanAgentResponse removeTraveller(BizplayPlanAgentRequest request,
            ConversationalAgentSession session, ObjectNode state, ArrayNode documents,
            String name, String message, String bizplayToken, boolean ko) {
        ArrayNode names = state.withArray("travelers");
        ArrayNode ids = state.withArray("travelerIds");
        int at = -1;
        for (int i = 0; i < names.size(); i++) {
            if (name.equals(names.get(i).asText(""))) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return null;
        }
        names.remove(at);
        if (at < ids.size()) {
            ids.remove(at);   // the two arrays are parallel — the document fan-out follows ids
        }
        if (state.path("routePointsByTraveller").isObject()) {
            ((ObjectNode) state.get("routePointsByTraveller")).remove(name);
        }
        syncTravelerDocuments(documents, state, request.getCorpUserId());
        if (!state.path("routePoints").isMissingNode()
                || state.path("routePointsByTraveller").size() > 0) {
            planEnrichmentService.previewRoutes(documents, state, bizplayToken);
        }
        List<String> left = travelerNames(state);
        String who = left == null || left.isEmpty() ? "" : String.join(", ", left);
        String reply = t(ko,
                name + " is off the trip. " + (who.isEmpty()
                        ? "Nobody is on the traveller list now — who should travel?"
                        : "Travelling now: " + who + ". Check the updated plan below."),
                name + " 님을 출장자에서 뺐어요. " + (who.isEmpty()
                        ? "출장자가 없는 상태예요 — 누가 가시나요?"
                        : "현재 출장자는 " + who + "예요. 아래에서 변경된 계획을 확인해 주세요."));
        session.setDraftJson(documents);
        appendTurn(session, "user", message);
        appendTurn(session, "assistant", reply);
        saveState(session, state);
        ConversationalAgentSession saved = sessionRepo.save(session);
        log.info("Traveller {} removed — {} document(s) remain.", name, documents.size());
        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent("TRAVELER_REMOVED")
                // The traveller list AND the per-traveller routes both changed — the client
                // redraws those cards instead of inferring it from the sentence.
                .uiRefresh(List.of("travellers", "route"))
                .subAgents(List.of("PLAN_AGENT"))
                .reply(reply)
                .travelers(travelerNames(state))
                .travelerIds(travelerIdList(state))
                .destination(state.path("destination").asText(null))
                .destinationCountry(state.path("destinationCountry").asText(null))
                .draftJson(saved.getDraftJson())
                .build();
    }

    /**
     * The alignment gate's second attempt. The session is rewound to exactly where the turn
     * began — same draft, same slots, same transcript — and the same message is run again with
     * the verifier's note in front of it, so the stages that missed the point are told what it
     * was. The note is stripped by {@link #stripChatContext} wherever the agent judges the
     * user's INTENT, so it informs the fill without ever being mistaken for a request.
     *
     * <p>Whatever the retry produces is what the user gets: one correction, then answer.
     */
    private BizplayPlanAgentResponse retryMisalignedTurn(BizplayPlanAgentRequest request,
            ConversationalAgentSession session, ArrayNode docsAtTurnStart,
            ObjectNode stateAtTurnStart, String issue, String bizplayToken) {
        log.warn("[VERIFY] gate: retrying the turn — {}", truncateForLog(issue));
        try {
            session.setDraftJson(docsAtTurnStart);
            saveState(session, stateAtTurnStart);
            sessionRepo.save(session);
        } catch (Exception e) {
            // Could not rewind: the first answer is still a real answer — do not lose it.
            log.warn("[VERIFY] gate: rewind failed ({}) — keeping the first attempt.",
                    e.getMessage());
            return null;
        }
        BizplayPlanAgentRequest again = new BizplayPlanAgentRequest();
        again.setCorpNo(request.getCorpNo());
        again.setCorpUserId(request.getCorpUserId());
        again.setSessionId(session.getId().toString());
        again.setTravelerCorpUserIds(request.getTravelerCorpUserIds());
        again.setDestinationDetail(request.getDestinationDetail());
        // Files were already consumed into the draft on the first pass; re-extracting them would
        // duplicate their facts, so the retry runs on the words alone.
        again.setMessage("(Note for this attempt — the previous one missed this: " + issue + ") "
                + (request.getMessage() == null ? "" : request.getMessage()));
        BizplayPlanAgentResponse second = chatTurn(again, bizplayToken, true);
        log.info("[VERIFY] gate: second attempt answered with intent {}",
                second == null ? "none" : second.getIntent());
        return second;
    }

    private static String truncateForLog(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private ObjectNode strippedDoc(ArrayNode documents) {
        if (documents.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        ObjectNode d = documents.get(0).deepCopy();
        d.remove("title");
        d.remove("content");
        // Absent, null, "" and 0 are all "no value" to the provider, and the writers freely turn
        // one into another (totalSettleAmount: missing -> 0) without anything real changing.
        // Dropping them from the comparison keeps such churn out of "did this turn edit the draft".
        java.util.List<String> emptyKeys = new java.util.ArrayList<>();
        d.fieldNames().forEachRemaining(k -> {
            JsonNode v = d.get(k);
            if (v == null || v.isNull()
                    || (v.isNumber() && v.asDouble() == 0.0)
                    || (v.isTextual() && v.asText().isBlank())) {
                emptyKeys.add(k);
            }
        });
        emptyKeys.forEach(d::remove);
        // issuedItems carry the form's own metadata plus mapper-COMPOSED display text (the route
        // summary, the lodge-request wording) that gets re-phrased on every turn — the same drift
        // as title/content, just nested. Canonicalize each item down to what a user can actually
        // EDIT: the item id and the selections' essential values (dates, region, memos). Dates
        // moved to "today" by an echo still register — the selections are kept verbatim.
        ArrayNode canon = objectMapper.createArrayNode();
        for (JsonNode it : d.withArray("issuedItems")) {
            ObjectNode c = canon.addObject();
            c.put("id", it.path("item").path("id").asLong());
            if ("BSTR_ROUTE".equals(it.path("item").path("itemType").asText(""))) {
                // The route's real data lives in bstrRoutes (the provider guide, §2) — anything
                // the mapper echoes into THIS item's selections is junk, stripped again at save.
                continue;
            }
            ArrayNode sels = c.putArray("s");
            for (JsonNode s : it.path("selections")) {
                ObjectNode cs = sels.addObject();
                for (String k : new String[]{"selectionId", "selectionName", "selectionErpCode",
                        "selectionMemo", "selectionContent"}) {
                    cs.put(k, s.path(k).asText(""));
                }
            }
        }
        d.set("issuedItems", canon);
        return d;
    }

    /** The editable slots that live in state rather than the document. */
    private String turnSignature(ArrayNode documents, ObjectNode state) {
        return state.path("travelers").toString()
                + "|" + state.path("travelerIds").toString()
                + "|" + state.path("resolvedTravelers").toString()
                + "|" + state.path("destination").asText("")
                + "|" + state.path("origin").asText("");
    }

    private boolean wantsToFileNow(String turnText, ConversationalAgentSession session, boolean ko) {
        if (turnText == null || turnText.isBlank() || turnText.length() > 120) {
            return false;
        }
        try {
            JsonNode got = slotFillerAgentService.extract(turnText, java.util.Map.of(
                    "fileNow", "\"yes\" ONLY if this message tells the assistant to go ahead and "
                            + "FILE/SUBMIT/CREATE the completed trip plan now (in any wording or "
                            + "language). Omit the field if the message adds or changes trip "
                            + "details, asks a question, or is anything else"),
                    ko, recentTurns(session));
            return "yes".equalsIgnoreCase(got.path("fileNow").asText(""));
        } catch (Exception e) {
            log.warn("File-now intent check failed ({}); not filing on this turn.", e.getMessage());
            return false;
        }
    }

    /** "출장자를 김도하로 바꿔줘" — the traveller list replaced with the named person. */
    private static final java.util.regex.Pattern TRAVELER_REPLACE = java.util.regex.Pattern.compile(
            "(?:출장자|여행자)\\s*(?:를|을|는|은)?\\s*([\\p{L}]{2,20}?)\\s*(?:으로|로)\\s*(?:바꾸|바꿔|변경|교체)");
    /** "김비플은 빼줘 / 제외해줘" — that person taken off the trip. */
    private static final java.util.regex.Pattern TRAVELER_REMOVE = java.util.regex.Pattern.compile(
            "([\\p{L}]{2,20}?)\\s*(?:를|을|는|은)?\\s*(?:빼|제외|삭제)");

    /**
     * Apply a traveller replacement or removal stated with its explicit construction. Grammar
     * normalisation like the destination change — and validation stays where it lives: the new
     * name goes through the SAME roster resolver as any other, so a name nobody recognises is
     * warned about and dropped rather than trusted.
     */
    private void backfillTravelerChange(ObjectNode state, String message,
                                        StringBuilder reply, boolean ko) {
        if (message == null || message.isBlank()) {
            return;
        }
        java.util.regex.Matcher m = TRAVELER_REPLACE.matcher(message);
        if (m.find()) {
            String name = m.group(1).trim();
            ArrayNode fresh = state.putArray("travelers");
            fresh.add(name);
            // Old resolutions AND old ids go: travelerIds is append-only everywhere else, and a
            // replace that left the previous ids in place still fanned documents out to people
            // who were just removed from the trip.
            state.remove("resolvedTravelers");
            state.putArray("travelerIds");
            reply.append(t(ko, "Changing the traveller to " + name + ". ",
                    "출장자를 " + name + "(으)로 변경할게요. "));
            log.info("Traveller list replaced with '{}' per the change construction.", name);
            return;
        }
        m = TRAVELER_REMOVE.matcher(message);
        if (m.find()) {
            String name = m.group(1).trim();
            ObjectNode resolved = state.withObject("resolvedTravelers");
            ArrayNode held = state.withArray("travelers");
            boolean removed = false;
            for (int i = held.size() - 1; i >= 0; i--) {
                String t2 = held.get(i).asText("");
                // The named person, matched loosely both ways (the list holds ROSTER names) -
                // and any name still UNRESOLVED: on a removal turn an unresolved name is the
                // mapper resurrecting someone from conversation history, not a new traveller.
                boolean named = t2.contains(name) || name.contains(t2);
                boolean unresolved = !resolved.has(t2);
                if (named || unresolved) {
                    if (resolved.get(t2) != null && resolved.get(t2).canConvertToLong()) {
                        removeTravelerId(state, resolved.get(t2).asLong());
                    }
                    resolved.remove(t2);
                    held.remove(i);
                    removed = removed || named;
                }
            }
            if (removed) {
                reply.append(t(ko, "Removed " + name + " from the trip. ",
                        name + "을(를) 출장자에서 제외했어요. "));
                log.info("Traveller '{}' removed per request.", name);
            }
        }
    }

    /** The inverse of addTravelerId - travelerIds was append-only until removal existed. */
    private void removeTravelerId(ObjectNode state, long id) {
        ArrayNode ids = state.withArray("travelerIds");
        for (int i = ids.size() - 1; i >= 0; i--) {
            if (ids.get(i).asLong() == id) {
                ids.remove(i);
            }
        }
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

    /** The user naming the title themselves - then whatever they wrote is the title. */
    private static final java.util.regex.Pattern TITLE_NAMED =
            java.util.regex.Pattern.compile("(?i)(제목|타이틀|title)");

    /** A "title" made only of dates and their connectives: 9월 10일부터 12일까지, 2026-09-10 ~ 09-12. */
    private static final java.util.regex.Pattern DATE_ONLY_TITLE =
            java.util.regex.Pattern.compile("^[\\d\\s월일년부터까지에서~/.,()-]+$");

    /**
     * Give the plan a title that says what the trip IS. The field mapper writes
     * basic:BASIC_TITLE from whatever the user last typed, so answering the dates question
     * with "9월 10일부터 12일까지" made that the trip title - every plan built step by step
     * came out named after a date range, which is exactly what makes a plan list unreadable
     * when several trips share a destination.
     * <p>Only replaces a blank or date-only title, and never touches one the user named
     * deliberately ("제목은 ..."), so a real title always wins.
     */
    private void ensureMeaningfulTitle(ObjectNode state, ArrayNode documents, String message,
                                       boolean ko) {
        if (documents.isEmpty() || !(documents.get(0) instanceof ObjectNode doc)) {
            return;
        }
        if (message != null && TITLE_NAMED.matcher(message).find()) {
            return;   // they said "제목" - whatever they wrote is the title they want
        }
        String title = doc.path("title").asText("").trim();
        String dest = state.path("destination").asText("").trim();
        String purpose = state.path("purpose").path("purposeName").asText("").trim();
        String segment = state.path("purpose").path("segmentName").asText("").trim();
        // A title that is just the PURPOSE ("해외출장") says nothing about this particular trip -
        // every overseas plan would carry the same name, which is exactly what makes a plan list
        // unreadable. Treated like a blank one: replaced once a destination is known.
        // The combined forms count too: the chip label is "해외출장 · 장기", and a title lifted from
        // it names the CATEGORY, not this trip - which is what showed up as "해외출장 · 일반".
        String squashed = title.replaceAll("[\\s·/,-]+", "");
        boolean genericTitle = title.equalsIgnoreCase(purpose) || title.equalsIgnoreCase(segment)
                || squashed.equalsIgnoreCase((purpose + segment).replaceAll("\\s+", ""))
                || squashed.equalsIgnoreCase((segment + purpose).replaceAll("\\s+", ""));
        // Provenance, not pattern-guessing: a title THIS method composed earlier is ours to
        // recompose when the destination moves — "도쿄 출장" going to 부산 is stale the moment the
        // change lands. A title the user wrote never matches composedTitle and is never touched.
        boolean oursFromBefore = !title.isBlank()
                && title.equals(state.path("composedTitle").asText("\0"));
        if (!title.isBlank() && !genericTitle && !oursFromBefore
                && !DATE_ONLY_TITLE.matcher(title).matches()) {
            return;   // already says something, and not something we wrote
        }
        String composed;
        if (!dest.isBlank()) {
            composed = ko ? dest + " 출장" : dest + " business trip";
        } else if (!purpose.isBlank()) {
            composed = purpose;
        } else {
            return;   // nothing to build one from yet - leave it for a later turn
        }
        if (!composed.equals(title)) {
            doc.put("title", composed);
            log.info("Plan title composed as '{}' - the mapper had left it as '{}'.",
                    composed, title);
        }
        state.put("composedTitle", composed);
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
        // "from September 21" is a DATE, not a departure place — the English "from X"
        // pattern must never turn a date word into the origin.
        if (DATEISH.matcher(place).find()) {
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
        String out = m;
        if (out != null && out.startsWith("(Current user:")) {
            out = out.replaceFirst("(?s)^\\(Current user:.*?no (Korean|English)\\.\\)\\s*", "").trim();
        }
        // The alignment gate prepends what the first attempt missed. It is guidance for the
        // FILL, never part of what the user asked — every intent judge sees the words alone.
        if (out != null && out.startsWith("(Note for this attempt")) {
            out = out.replaceFirst("(?s)^\\(Note for this attempt[^)]*\\)\\s*", "").trim();
        }
        return out;
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
        // A real QUESTION wins even when it contains an edit word — "누구를 넣었지?" is
        // asking about the past, not requesting an add.
        boolean asked = m.contains("?")
                && m.matches("(?ius).*(what|which|who|when|where|how|did|"
                + "뭐|무엇|누구|언제|어디|몇|였|했|넣었|인가|나요|까요|었지|았지).*");
        if (asked) {
            return true;
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

    /**
     * The last few real turns, oldest first, for sub-agents that can use context. Deliberately
     * short: two exchanges is enough to resolve "the second one" or "make it the day after", and
     * more only raises the chance the model lifts a stale value out of the history. The
     * {@code agent_state} entry is skipped - it is bookkeeping, not conversation.
     */
    /**
     * Is this message about the plan being drafted right now — its fields, its travellers, its
     * route, or filing it — rather than a separate request? Asked only when a custom agent has
     * claimed the turn, so the common path pays nothing.
     */
    private boolean aboutThisDraft(String message, boolean ko) {
        if (message == null || message.isBlank()) {
            return false;
        }
        try {
            JsonNode got = slotFillerAgentService.extract(message, java.util.Map.of(
                    "subject", "The user is in the middle of drafting a BUSINESS TRIP PLAN. "
                            + "Which is this message about? Answer exactly one word. \"plan\" - "
                            + "the trip plan being drafted: its travellers, dates, title, purpose, "
                            + "destination, travel route, transport, any of its fields, or filing "
                            + "it. \"other\" - a separate request that is not about that draft "
                            + "(looking up information, another system, small talk)"),
                    ko, java.util.List.<String>of());
            return "plan".equalsIgnoreCase(got.path("subject").asText("").trim());
        } catch (Exception e) {
            return false;   // unknown -> leave the routing exactly as it was
        }
    }

    /** Per (session, message) memo so the answer-first judge below runs once per turn. */
    private final java.util.Map<String, Boolean> answersAskCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * ANSWER-FIRST. When the agent has just asked something, the next message is read as the
     * ANSWER to that question before anything else gets to reinterpret it. Only when it does
     * NOT answer the question do the opportunistic judges run (traveller removal, trip-type
     * change) and try to work out what else the user wants.
     *
     * <p>Without this, a message that merely NAMED a person while the approval-line question was
     * on screen ("김도하 as approval") was read by the removal judge as "take 김도하 off the
     * trip". The question the agent itself asked is the strongest context there is; it should
     * outrank a judge that only sees the sentence.
     */
    private boolean answersPendingAsk(ConversationalAgentSession session, ObjectNode state,
                                      String message, boolean ko) {
        if (session == null || message == null || message.isBlank()) {
            return false;
        }
        String asked = lastAssistantAsk(session);
        boolean pending = state != null && (state.has("pendingAsk")
                || state.path("pendingDestinationAsk").asBoolean(false)
                || state.path("pendingTransportAsk").asBoolean(false)
                || state.path("pendingOriginAsk").asBoolean(false)
                || state.path("pendingDetailAsk").asBoolean(false)
                || state.path("pendingRouteAsk").asBoolean(false));
        if (asked == null || asked.isBlank() || !(pending || asked.contains("?"))) {
            return false;   // nothing was asked — there is no answer to prioritise
        }
        String key = session.getId() + "#" + message;
        Boolean memo = answersAskCache.get(key);
        if (memo != null) {
            return memo;
        }
        boolean answers = false;
        try {
            JsonNode got = slotFillerAgentService.extract(stripChatContext(message),
                    java.util.Map.of("role", "The assistant just asked the user: \"" + asked
                            + "\" Answer with exactly one word. \"answer\" - the user's message "
                            + "is an ANSWER to THAT question (a value, a name, a choice, or a "
                            + "refusal such as \"none\"/\"skip\"/\"없어\"). \"other\" - it asks "
                            + "for something different instead: changing another field, taking "
                            + "someone off the trip, changing the form type, or a question of "
                            + "their own"), ko, java.util.List.<String>of());
            answers = "answer".equalsIgnoreCase(got.path("role").asText("").trim());
        } catch (Exception e) {
            log.warn("Answer-first judge failed ({}) — falling back to the other judges",
                    e.getMessage());
        }
        if (answersAskCache.size() > 500) {
            answersAskCache.clear();
        }
        answersAskCache.put(key, answers);
        log.info("Answer-first: '{}' vs pending ask '{}' -> {}", truncateForLog(message),
                truncateForLog(asked), answers ? "ANSWER (other judges skipped)" : "not an answer");
        return answers;
    }

    /** The last thing the agent said, which is what any pending question was worded as. */
    private String lastAssistantAsk(ConversationalAgentSession session) {
        JsonNode events = session.getChatEventJson();
        if (events == null || !events.isArray()) {
            return null;
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            if ("assistant".equals(events.get(i).path("role").asText(""))) {
                String c = events.get(i).path("content").asText("").replaceAll("\s+", " ").trim();
                return c.length() > 300 ? c.substring(c.length() - 300) : c;
            }
        }
        return null;
    }

    private java.util.List<String> recentTurns(ConversationalAgentSession session) {
        java.util.List<String> out = new ArrayList<>();
        JsonNode events = session.getChatEventJson();
        if (events == null || !events.isArray()) {
            return out;
        }
        java.util.List<JsonNode> chat = new ArrayList<>();
        for (JsonNode e : events) {
            String role = e.path("role").asText("");
            if ("user".equals(role) || "assistant".equals(role)) {
                chat.add(e);
            }
        }
        for (JsonNode e : chat.subList(Math.max(0, chat.size() - RECENT_TURNS), chat.size())) {
            String content = e.path("content").asText("").replaceAll("\s+", " ").trim();
            if (!content.isEmpty()) {
                out.add(e.path("role").asText("") + ": " + (content.length() > 300
                        ? content.substring(0, 300) : content));
            }
        }
        return out;
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
