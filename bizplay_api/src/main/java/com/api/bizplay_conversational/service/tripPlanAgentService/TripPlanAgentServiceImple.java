package com.api.bizplay_conversational.service.tripPlanAgentService;

import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.request.StaffLookupAgentRequest;
import com.api.bizplay_conversational.model.request.TripPlanAgentRequest;
import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.response.SessionDetailResponse;
import com.api.bizplay_conversational.model.response.SessionSummaryResponse;
import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.DatabaseLookupAgentResponse;
import com.api.bizplay_conversational.model.response.DraftEditPlan;
import com.api.bizplay_conversational.model.response.MissingFieldsResult;
import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.StaffLookupAgentResponse;
import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.api.bizplay_conversational.model.response.TravelerResolution;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.api.bizplay_conversational.repository.ConversationalAgentSessionRepo;
import com.api.bizplay_conversational.service.clarificationAgentService.ClarificationAgentService;
import com.api.bizplay_conversational.service.databaseLookupAgentService.DatabaseLookupAgentService;
import com.api.bizplay_conversational.service.pdfAgentService.PdfAgentService;
import com.api.bizplay_conversational.service.requiredFieldValidationService.RequiredFieldValidationService;
import com.api.bizplay_conversational.service.updateAgentService.UpdateAgentService;
import com.api.bizplay_conversational.service.fileExtractionService.FileExtractionService;
import com.api.bizplay_conversational.service.fileExtractionService.UploadedFile;
import com.api.bizplay_conversational.service.requestBodyBuilderService.RequestBodyBuilderService;
import com.api.bizplay_conversational.service.spreadsheetAgentService.SpreadsheetAgentService;
import com.api.bizplay_conversational.service.staffLookupAgentService.StaffLookupAgentService;
import com.api.bizplay_conversational.service.textAnalysisAgentService.TextAnalysisAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class TripPlanAgentServiceImple implements TripPlanAgentService {

    /** Most recent turns (user + assistant) fed back to the model as context. */
    private static final int MAX_HISTORY_TURNS = 20;

    private final Map<String, ChatClient> chatClientRegistry;
    private final com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService llmSettingsService;
    private final DatabaseLookupAgentService databaseLookupAgentService;
    private final StaffLookupAgentService staffLookupAgentService;
    private final TextAnalysisAgentService textAnalysisAgentService;
    private final SpreadsheetAgentService spreadsheetAgentService;
    private final PdfAgentService pdfAgentService;
    private final FileExtractionService fileExtractionService;
    private final RequestBodyBuilderService requestBodyBuilderService;
    private final RequiredFieldValidationService requiredFieldValidationService;
    private final ClarificationAgentService clarificationAgentService;
    private final UpdateAgentService updateAgentService;
    private final ConversationalAgentSessionRepo sessionRepo;
    private final ObjectMapper objectMapper;
    private final Executor agentTaskExecutor;

    @Value("${app.conversational.trip-plan-agent.model:qwen3-14b}")
    private String modelName;

    public TripPlanAgentServiceImple(
            Map<String, ChatClient> chatClientRegistry,
            com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService llmSettingsService,
            DatabaseLookupAgentService databaseLookupAgentService,
            StaffLookupAgentService staffLookupAgentService,
            TextAnalysisAgentService textAnalysisAgentService,
            SpreadsheetAgentService spreadsheetAgentService,
            PdfAgentService pdfAgentService,
            FileExtractionService fileExtractionService,
            RequestBodyBuilderService requestBodyBuilderService,
            RequiredFieldValidationService requiredFieldValidationService,
            ClarificationAgentService clarificationAgentService,
            UpdateAgentService updateAgentService,
            ConversationalAgentSessionRepo sessionRepo,
            ObjectMapper objectMapper,
            @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.databaseLookupAgentService = databaseLookupAgentService;
        this.staffLookupAgentService = staffLookupAgentService;
        this.textAnalysisAgentService = textAnalysisAgentService;
        this.spreadsheetAgentService = spreadsheetAgentService;
        this.pdfAgentService = pdfAgentService;
        this.fileExtractionService = fileExtractionService;
        this.requestBodyBuilderService = requestBodyBuilderService;
        this.requiredFieldValidationService = requiredFieldValidationService;
        this.clarificationAgentService = clarificationAgentService;
        this.updateAgentService = updateAgentService;
        this.sessionRepo = sessionRepo;
        this.objectMapper = objectMapper;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> listSessions(String corpNo) {
        return sessionRepo.findByCorpNo(corpNo).stream()
                .map(s -> SessionSummaryResponse.builder()
                        .sessionId(s.getId())
                        .corpNo(s.getCorpNo())
                        .agentType(s.getAgentType() == null ? null : s.getAgentType().name())
                        .status(s.getStatus() == null ? null : s.getStatus().name())
                        .createdDate(s.getCreatedDate())
                        .updatedDate(s.getUpdatedDate())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SessionDetailResponse getSession(String sessionId) {
        UUID id;
        try {
            id = UUID.fromString(sessionId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomNotFoundException("Invalid session id: " + sessionId);
        }
        ConversationalAgentSession s = sessionRepo.findById(id)
                .orElseThrow(() -> new CustomNotFoundException("Session not found: " + sessionId));
        return toDetail(s);
    }

    @Override
    @Transactional
    public SessionDetailResponse saveDraft(String sessionId, JsonNode draftJson) {
        if (draftJson == null || draftJson.isNull() || !draftJson.isObject()) {
            throw new IllegalArgumentException("A draft object body is required.");
        }
        UUID id;
        try {
            id = UUID.fromString(sessionId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomNotFoundException("Invalid session id: " + sessionId);
        }
        ConversationalAgentSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new CustomNotFoundException("Session not found: " + sessionId));

        // Overwrite the draft with the client's edited copy, then run the same normalization and
        // validation pass as a chat turn so the saved draft stays consistent (shared route inherited,
        // type/title defaulted, missing fields recomputed, status flipped) and resumes cleanly.
        session.setDraftJson(draftJson);
        requestBodyBuilderService.inheritCommonRoute(session);
        TripPlanDraft draft = requestBodyBuilderService.snapshot(session);
        MissingFieldsResult missing = requiredFieldValidationService.validate(draft);
        requestBodyBuilderService.stampMissingFields(session, missing.getMissing());
        session.setStatus(missing.isComplete()
                ? ConversationalAgentSession.AgentStatus.READY_FOR_REVIEW
                : ConversationalAgentSession.AgentStatus.COLLECTING);
        sessionRepo.save(session);

        // Reload so DB-generated timestamps (updated_date via NOW()) are returned.
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);
        return toDetail(saved);
    }

    private SessionDetailResponse toDetail(ConversationalAgentSession s) {
        return SessionDetailResponse.builder()
                .sessionId(s.getId())
                .corpNo(s.getCorpNo())
                .agentType(s.getAgentType() == null ? null : s.getAgentType().name())
                .status(s.getStatus() == null ? null : s.getStatus().name())
                .draftJson(s.getDraftJson())
                .chatEventJson(s.getChatEventJson())
                .createdDate(s.getCreatedDate())
                .updatedDate(s.getUpdatedDate())
                .build();
    }

    @Override
    @Transactional
    public TripPlanAgentResponse chat(TripPlanAgentRequest request) {
        List<String> fileIds = request.allFileIds();
        boolean hasFiles = !fileIds.isEmpty();
        boolean hasMessage = request.getMessage() != null && !request.getMessage().isBlank();
        if (!hasFiles && !hasMessage) {
            throw new IllegalArgumentException("Either message or at least one fileId is required.");
        }

        ConversationalAgentSession session = resolveSession(request);
        List<Message> history = toMessages(session.getChatEventJson());

        // Any uploaded file(s) deterministically trigger the file agents, routed by type
        // (Excel -> Spreadsheet Agent, PDF -> PDF Agent) — no LLM intent classification needed.
        // A bare approval ("yes" / "approve" / "create") is handled directly: creation is done by the
        // UI button, so we just point the user there instead of routing it to the Update Agent.
        // Re-resolve travelers awaiting the staff DB on EVERY turn: staff registered since a prior turn
        // now get added (with the route held from the original extraction), and a duplicate-name pick in
        // this message is applied.
        // Detect a skip/decline for a still-pending ambiguous name BEFORE any resolution or lookup, so
        // "skip traveler: X" / "don't add X" never re-triggers staff lookup and loops forever.
        String skipName = (hasMessage && !hasFiles)
                ? detectPendingSkip(request.getMessage(), session)
                : null;

        List<String> pickedTravelers = (skipName == null)
                ? requestBodyBuilderService.resolvePendingTravelers(session, hasMessage ? request.getMessage() : null)
                : List.of();

        String intent;
        if (skipName != null) {
            intent = "SKIP";
        } else if (hasFiles) {
            intent = "FILE_UPLOAD";
        } else if (isApprovalPhrase(request.getMessage())) {
            intent = "APPROVE";
        } else if (!pickedTravelers.isEmpty()) {
            intent = "DISAMBIGUATE";
        } else {
            intent = classifyIntent(request.getMessage(), history);
        }

        TripPlanAgentResponse.TripPlanAgentResponseBuilder response = TripPlanAgentResponse.builder()
                .intent(intent);
        String assistantTurn;
        // Collected per turn; the response is built once after the (optional) clarification pass.
        List<String> subAgents = new ArrayList<>();
        boolean delegated = false;
        // Whether this turn actually built/changed the draft (gates the clarification pass).
        boolean draftModified = false;
        String reply;
        // Structured disambiguation options for this turn (non-null only when a name is ambiguous).
        List<TripPlanAgentResponse.PendingChoice> pendingChoices = null;

        if ("FILE_UPLOAD".equals(intent)) {
            // Fetch every uploaded file and route by type: Excel -> Spreadsheet Agent (WHO),
            // PDF -> PDF Agent (WHERE/WHEN/WHY). The message (if any) -> Text Analysis Agent.
            List<UploadedFile> excelFiles = new ArrayList<>();
            List<UploadedFile> pdfFiles = new ArrayList<>();
            List<String> notFound = new ArrayList<>();
            List<String> unsupported = new ArrayList<>();
            for (String fid : fileIds) {
                UploadedFile f = fileExtractionService.get(fid).orElse(null);
                if (f == null) {
                    notFound.add(fid);
                } else if (isExcel(f)) {
                    excelFiles.add(f);
                } else if (isPdf(f)) {
                    pdfFiles.add(f);
                } else {
                    unsupported.add(f.filename() != null ? f.filename() : fid);
                }
            }

            // Fan out all file agents + the message analysis in parallel.
            List<CompletableFuture<SpreadsheetAnalysisResult>> sheetFutures = new ArrayList<>();
            for (UploadedFile f : excelFiles) {
                sheetFutures.add(CompletableFuture.supplyAsync(
                        () -> spreadsheetAgentService.analyze(request.getCorpNo(), f.content(), f.filename()),
                        agentTaskExecutor));
            }
            List<CompletableFuture<TextAnalysisResult>> pdfFutures = new ArrayList<>();
            for (UploadedFile f : pdfFiles) {
                pdfFutures.add(CompletableFuture.supplyAsync(
                        () -> pdfAgentService.analyze(request.getCorpNo(), f.content(), f.filename(), history),
                        agentTaskExecutor));
            }
            CompletableFuture<TextAnalysisResult> msgFuture = hasMessage
                    ? CompletableFuture.supplyAsync(
                            () -> textAnalysisAgentService.analyze(request.getMessage(), history), agentTaskExecutor)
                    : CompletableFuture.completedFuture(null);

            List<CompletableFuture<?>> allFutures = new ArrayList<>();
            allFutures.addAll(sheetFutures);
            allFutures.addAll(pdfFutures);
            allFutures.add(msgFuture);
            CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();

            // Merge sequentially (single-threaded) to avoid a read/modify/write race on draft_json.
            // Priority order: spreadsheets establish travelers; the user's MESSAGE is applied
            // AUTHORITATIVELY (text wins on conflicts); PDFs are applied only as SUPPLEMENTARY fillers.
            long staffAdded = 0;
            for (CompletableFuture<SpreadsheetAnalysisResult> fut : sheetFutures) {
                SpreadsheetAnalysisResult sheet = fut.join();
                requestBodyBuilderService.mergeSpreadsheet(session, sheet);
                if (sheet != null && sheet.getMatched() != null) {
                    staffAdded += sheet.getMatched().stream().filter(s -> s != null && s.isMatched()).count();
                }
            }

            // Travelers that couldn't be added (not found / ambiguous), accumulated for the reply.
            TravelerResolution turnRes = new TravelerResolution();

            // Text (the message) is the priority source — merged first and authoritatively.
            TextAnalysisResult msgAnalysis = msgFuture.join();
            if (msgAnalysis != null) {
                turnRes.merge(requestBodyBuilderService.mergeTextAnalysis(session, msgAnalysis, true));
            }

            // PDFs are supplementary. A PDF with no usable trip info is skipped entirely. Otherwise:
            //  - if it aligns with the message (same trip), merge its trip-level fields (fill blanks)
            //    AND its travelers;
            //  - if it describes a DIFFERENT trip than the authoritative message, we still take the
            //    people the user attached (travelers only), but NOT the conflicting trip-level details.
            int pdfUsed = 0;
            int pdfSkipped = 0;
            int pdfTravelersOnly = 0;
            for (int i = 0; i < pdfFutures.size(); i++) {
                TextAnalysisResult pdf = pdfFutures.get(i).join();
                UploadedFile f = pdfFiles.get(i);
                if (!isRelevantTripDoc(pdf)) {
                    log.info("Ignoring PDF '{}' (no usable trip information).", f.filename());
                    pdfSkipped++;
                    continue;
                }
                if (alignsWith(msgAnalysis, pdf)) {
                    // Authoritative only when there is no message to defer to; otherwise fill blanks only.
                    turnRes.merge(requestBodyBuilderService.mergeTextAnalysis(session, pdf, msgAnalysis == null));
                } else {
                    log.info("PDF '{}' describes a different trip; taking its travelers only.", f.filename());
                    turnRes.merge(requestBodyBuilderService.mergeTravelersOnly(session, pdf));
                    pdfTravelersOnly++;
                }
                requestBodyBuilderService.mergeAttachment(session, f.fileId());
                pdfUsed++;
            }

            // Record spreadsheet files as attachments (PDFs are attached above only when actually used).
            for (UploadedFile f : excelFiles) {
                requestBodyBuilderService.mergeAttachment(session, f.fileId());
            }

            if (!excelFiles.isEmpty()) {
                subAgents.add("SPREADSHEET_AGENT");
            }
            if (pdfUsed > 0) {
                subAgents.add("PDF_AGENT");
            }
            if (msgAnalysis != null) {
                subAgents.add("TEXT_ANALYSIS_AGENT");
            }

            StringBuilder sb = new StringBuilder();
            if (!excelFiles.isEmpty()) {
                sb.append("Read ").append(excelFiles.size()).append(" spreadsheet(s) and added ")
                        .append(staffAdded).append(" staff member(s). ");
            }
            if (msgAnalysis != null) {
                sb.append("Captured trip details from your message. ");
            }
            if (pdfUsed > 0) {
                sb.append("Used ").append(pdfUsed).append(" PDF document(s). ");
            }
            if (pdfTravelersOnly > 0) {
                sb.append("Added travelers from ").append(pdfTravelersOnly)
                        .append(" attached document(s) (trip details kept from your message). ");
            }
            if (pdfSkipped > 0) {
                sb.append("Ignored ").append(pdfSkipped).append(" PDF(s) with no usable trip information. ");
            }
            if (!notFound.isEmpty()) {
                sb.append("Could not find file(s): ").append(String.join(", ", notFound)).append(". ");
            }
            if (!unsupported.isEmpty()) {
                sb.append("Skipped unsupported file type(s): ").append(String.join(", ", unsupported)).append(". ");
            }
            appendResolutionNotes(sb, turnRes);
            pendingChoices = buildPendingChoices(turnRes);
            reply = sb.toString().trim();
            if (reply.isEmpty()) {
                reply = "I could not process the uploaded file(s).";
            }
            delegated = !subAgents.isEmpty();
            draftModified = !excelFiles.isEmpty() || msgAnalysis != null || pdfUsed > 0;
            assistantTurn = reply;
        } else if ("TEXT_ANALYSIS".equals(intent)) {
            // Fan out the data-only sub-agents in parallel: Staff Lookup (who) and
            // Text Analysis (trip info). Neither touches draft_json; the RequestBody
            // builder constructs the draft from their combined outputs.
            StaffLookupAgentRequest staffRequest = new StaffLookupAgentRequest();
            staffRequest.setCorpNo(request.getCorpNo());
            staffRequest.setMessage(request.getMessage());

            CompletableFuture<StaffLookupAgentResponse> staffFuture =
                    CompletableFuture.supplyAsync(() -> staffLookupAgentService.lookup(staffRequest), agentTaskExecutor);
            CompletableFuture<TextAnalysisResult> analysisFuture =
                    CompletableFuture.supplyAsync(() -> textAnalysisAgentService.analyze(request.getMessage(), history), agentTaskExecutor);
            CompletableFuture.allOf(staffFuture, analysisFuture).join();

            StaffLookupAgentResponse staffLookup = staffFuture.join();
            TextAnalysisResult analysis = analysisFuture.join();

            // Merge sequentially into draft_json (single-threaded) to avoid a read/modify/write race.
            boolean staffMatched = staffLookup.getResult() != null && staffLookup.getResult().isMatched();
            if (staffMatched) {
                requestBodyBuilderService.mergeStaff(session, staffLookup.getResult());
            }
            TravelerResolution textRes = requestBodyBuilderService.mergeTextAnalysis(session, analysis);

            StringBuilder textSb = new StringBuilder("I updated the trip plan draft from your message"
                    + (staffMatched ? " (staff: " + staffLookup.getResult().getStaffName() + ")" : "") + ". ");
            appendResolutionNotes(textSb, textRes);
            pendingChoices = buildPendingChoices(textRes);
            reply = textSb.toString().trim();
            subAgents.add("STAFF_LOOKUP_AGENT");
            subAgents.add("TEXT_ANALYSIS_AGENT");
            delegated = true;
            draftModified = true;
            assistantTurn = reply;
        } else if ("UPDATE".equals(intent)) {
            // Edit an existing draft: the Update Agent (data-only) turns the NL request into a
            // validated op list; the builder applies it against a fixed allowlist.
            TripPlanDraft current = requestBodyBuilderService.snapshot(session);
            DraftEditPlan plan = updateAgentService.plan(request.getMessage(), current, history);
            List<String> applied = requestBodyBuilderService.applyEdits(session, plan);
            subAgents.add("UPDATE_AGENT");
            delegated = true;
            // A duplicate-name "add" doesn't add a traveler — it holds candidates pending so the user
            // can choose. Surface that here instead of silently picking the first match.
            com.api.bizplay_conversational.model.response.TravelerResolution res =
                    requestBodyBuilderService.pendingResolution(session);
            boolean needsPick = res != null && !res.getAmbiguous().isEmpty();
            StringBuilder updateSb = new StringBuilder();
            if (!applied.isEmpty()) {
                draftModified = true;
                updateSb.append("Applied ").append(applied.size()).append(" update(s): ")
                        .append(String.join("; ", applied)).append(". ");
            }
            if (needsPick) {
                draftModified = true; // persist the reply + keep the draft in COLLECTING
                appendResolutionNotes(updateSb, res);
                pendingChoices = buildPendingChoices(res);
            }
            if (updateSb.length() == 0) {
                updateSb.append("I couldn't find a valid change to make from that. Could you rephrase what to update?");
            }
            reply = updateSb.toString().trim();
            assistantTurn = reply;
        } else if ("STAFF_LOOKUP".equals(intent)) {
            // Fixed-query path: a single, well-defined "who is X" lookup. Deterministic
            // name -> staffId resolution, no LLM-generated SQL.
            StaffLookupAgentRequest staffRequest = new StaffLookupAgentRequest();
            staffRequest.setCorpNo(request.getCorpNo());
            staffRequest.setMessage(request.getMessage());
            StaffLookupAgentResponse staffLookup = staffLookupAgentService.lookup(staffRequest);
            boolean matched = staffLookup.getResult() != null && staffLookup.getResult().isMatched();
            reply = matched
                    ? "I found the staff member you asked about."
                    : "I could not find a matching staff member.";
            subAgents.add("STAFF_LOOKUP_AGENT");
            delegated = true;
            if (matched) {
                requestBodyBuilderService.mergeStaff(session, staffLookup.getResult());
                draftModified = true;
            }
            assistantTurn = reply
                    + (matched ? " (" + staffLookup.getResult().getStaffName() + ")" : "");
        } else if ("DATABASE_LOOKUP".equals(intent)) {
            DatabaseLookupAgentResponse lookup = databaseLookupAgentService.lookup(
                    request.getCorpNo(),
                    request.getMessage(),
                    history
            );
            reply = lookup.isExecuted()
                    ? "I looked up the requested department/staff/traveler data."
                    : "I could not complete the database lookup.";
            subAgents.add("DATABASE_LOOKUP_AGENT");
            delegated = true;
            // Persist the generated SQL so follow-ups (e.g. "also join the trip") can extend it.
            assistantTurn = lookup.getSql() != null ? lookup.getSql() : reply;
        } else if ("DISAMBIGUATE".equals(intent)) {
            // The user picked which staff member an ambiguous (duplicate) traveler name refers to.
            reply = "Added " + String.join(", ", pickedTravelers) + " to the trip.";
            subAgents.add("STAFF_LOOKUP_AGENT");
            delegated = true;
            draftModified = true;
            assistantTurn = reply;
        } else if ("SKIP".equals(intent)) {
            // The user declined a pending ambiguous mention. Drop it WITHOUT any staff lookup or text
            // analysis (which would re-detect the same duplicates and loop). Confirm and move on.
            String removed = requestBodyBuilderService.removePendingTraveler(session, skipName);
            subAgents.add("UPDATE_AGENT");
            delegated = true;
            if (removed != null) {
                draftModified = true;
                reply = "Okay — not adding '" + removed + "'.";
                // If other ambiguous mentions remain, keep offering them.
                pendingChoices = buildPendingChoices(requestBodyBuilderService.pendingResolution(session));
            } else {
                reply = "Okay — '" + skipName + "' wasn't pending, so nothing changed.";
            }
            assistantTurn = reply;
        } else if ("APPROVE".equals(intent)) {
            // Creation is performed by the UI "Create this plan" button, not by a chat reply. Point the
            // user there rather than trying to interpret the affirmation as an edit.
            reply = "Whenever you're ready, click \"Create this plan\" on the right to finish. "
                    + "If you'd like any changes first, just tell me what to update.";
            assistantTurn = reply;
        } else {
            reply = "I can help create a trip plan. For now, this initial setup delegates department, staff, and traveler lookup tasks to the database lookup sub-agent.";
            assistantTurn = reply;
        }

        // If pending travelers were auto-resolved (newly-registered staff) alongside another action,
        // note them and make sure the clarification pass runs.
        if (!pickedTravelers.isEmpty() && !"DISAMBIGUATE".equals(intent)) {
            reply = "Added previously pending traveler(s): " + String.join(", ", pickedTravelers) + ". " + reply;
            assistantTurn = reply;
            draftModified = true;
        }

        // Clarification / Field-Completion pass: only after a turn that actually built the draft.
        // Validate required fields, record what is missing on the draft, flip the session status,
        // and let the Clarification Agent phrase the follow-up (or a ready-to-create confirmation).
        if (draftModified) {
            // One session = one business trip: backfill each traveler's blank route fields from the
            // trip's shared values (so staff added later inherit origin/destination/return/transport).
            requestBodyBuilderService.inheritCommonRoute(session);
            TripPlanDraft draft = requestBodyBuilderService.snapshot(session);
            MissingFieldsResult missing = requiredFieldValidationService.validate(draft);
            requestBodyBuilderService.stampMissingFields(session, missing.getMissing());
            session.setStatus(missing.isComplete()
                    ? ConversationalAgentSession.AgentStatus.READY_FOR_REVIEW
                    : ConversationalAgentSession.AgentStatus.COLLECTING);
            String followUp = clarificationAgentService.composeFollowUp(draft, missing, history);
            if (followUp != null && !followUp.isBlank()) {
                reply = reply + " " + followUp;
                assistantTurn = reply;
            }
            subAgents.add("CLARIFICATION_AGENT");
        }

        response.delegated(delegated).subAgents(subAgents).reply(reply).pendingChoices(pendingChoices);

        String userTurn = hasMessage
                ? request.getMessage()
                : "[uploaded file(s): " + String.join(", ", fileIds) + "]";
        appendTurn(session, "user", userTurn);
        appendTurn(session, "assistant", assistantTurn);
        sessionRepo.save(session);

        // Reload so DB-generated timestamps (created_date / updated_date via NOW()) are returned.
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

        return response
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .draftJson(saved.getDraftJson())
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    private ConversationalAgentSession resolveSession(TripPlanAgentRequest request) {
        String sessionId = request.getSessionId();

        // No sessionId -> start a brand-new session.
        if (sessionId == null || sessionId.isBlank()) {
            return newSession(request);
        }

        // sessionId provided -> it MUST resolve to an existing session for this corp; otherwise fail
        // (do not silently fork a new session, which hides client mistakes).
        UUID id;
        try {
            id = UUID.fromString(sessionId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomNotFoundException("Invalid sessionId: " + sessionId);
        }
        ConversationalAgentSession existing = sessionRepo.findById(id)
                .orElseThrow(() -> new CustomNotFoundException("Session not found: " + sessionId));
        if (!request.getCorpNo().equals(existing.getCorpNo())
                || !ConversationalAgentSession.AgentType.TRIP_PLAN.equals(existing.getAgentType())) {
            throw new CustomNotFoundException(
                    "Session " + sessionId + " does not belong to corpNo=" + request.getCorpNo()
                            + " or is not a TRIP_PLAN session.");
        }
        return existing;
    }

    private ConversationalAgentSession newSession(TripPlanAgentRequest request) {
        ConversationalAgentSession created = new ConversationalAgentSession();
        created.setCorpNo(request.getCorpNo());
        created.setAgentType(ConversationalAgentSession.AgentType.TRIP_PLAN);
        return created;
    }

    private List<Message> toMessages(JsonNode chatEventJson) {
        List<Message> messages = new ArrayList<>();
        if (chatEventJson == null || !chatEventJson.isArray()) {
            return messages;
        }
        int from = Math.max(0, chatEventJson.size() - MAX_HISTORY_TURNS);
        for (int i = from; i < chatEventJson.size(); i++) {
            JsonNode turn = chatEventJson.get(i);
            String role = turn.path("role").asText("");
            String content = turn.path("content").asText("");
            if (content.isBlank()) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(role)) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    private void appendTurn(ConversationalAgentSession session, String role, String content) {
        JsonNode existing = session.getChatEventJson();
        ArrayNode events = (existing instanceof ArrayNode)
                ? (ArrayNode) existing
                : objectMapper.createArrayNode();
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", role);
        turn.put("content", content == null ? "" : content);
        turn.put("created_date", java.time.LocalDateTime.now().toString());
        events.add(turn);
        session.setChatEventJson(events);
    }

    /** Excel staff list: route to the Spreadsheet Agent. */
    private boolean isExcel(UploadedFile f) {
        String name = f.filename() == null ? "" : f.filename().toLowerCase(Locale.ROOT);
        String ct = f.contentType() == null ? "" : f.contentType().toLowerCase(Locale.ROOT);
        return name.endsWith(".xlsx") || name.endsWith(".xls")
                || ct.contains("spreadsheet") || ct.contains("excel") || ct.contains("ms-excel");
    }

    /** PDF trip document: route to the PDF Agent. */
    private boolean isPdf(UploadedFile f) {
        String name = f.filename() == null ? "" : f.filename().toLowerCase(Locale.ROOT);
        String ct = f.contentType() == null ? "" : f.contentType().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || ct.contains("pdf");
    }

    /**
     * A PDF result is worth using only if it actually extracted some trip information. A PDF that is
     * not a trip document (a contract, a random attachment) yields nothing usable and is ignored.
     */
    private boolean isRelevantTripDoc(TextAnalysisResult r) {
        if (r == null) {
            return false;
        }
        return notBlank(r.getTripDestination())
                || notBlank(r.getBusinessPeriod())
                || notBlank(r.getBusinessStartDate())
                || notBlank(r.getBusinessEndDate())
                || notBlank(r.getTripType())
                || (r.getTravelers() != null && !r.getTravelers().isEmpty());
    }

    /**
     * Does the PDF describe the SAME trip as the user's message? If both name a trip destination and
     * they clearly differ, the PDF is about a different trip and must not contaminate this draft.
     * When there is no message (PDF-only turn), there is nothing to align against, so it passes.
     */
    private boolean alignsWith(TextAnalysisResult message, TextAnalysisResult pdf) {
        if (message == null) {
            return true;
        }
        String md = message.getTripDestination();
        String pd = pdf.getTripDestination();
        if (notBlank(md) && notBlank(pd)) {
            String a = md.trim().toLowerCase(Locale.ROOT);
            String b = pd.trim().toLowerCase(Locale.ROOT);
            return a.contains(b) || b.contains(a);
        }
        return true;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Append messages about extracted travelers that could not be added: names not found in the staff
     * DB, and ambiguous (duplicate) names whose candidates the user must choose between.
     */
    private void appendResolutionNotes(StringBuilder sb, com.api.bizplay_conversational.model.response.TravelerResolution res) {
        if (res == null) {
            return;
        }
        if (!res.getNotFound().isEmpty()) {
            sb.append("These traveler name(s) are not in the staff database: ")
                    .append(String.join(", ", res.getNotFound()))
                    .append(" — add them to staff first, or check the spelling. ");
        }
        for (com.api.bizplay_conversational.model.response.TravelerResolution.Ambiguous a : res.getAmbiguous()) {
            List<String> opts = new ArrayList<>();
            for (com.api.bizplay_conversational.model.response.StaffLookupResult c : a.getCandidates()) {
                String d = c.getDepartmentName() != null ? c.getDepartmentName() : "?";
                String p = c.getPosition() != null ? c.getPosition() : "?";
                opts.add(c.getStaffName() + " (" + d + " / " + p + ")");
            }
            sb.append("Multiple staff match '").append(a.getName()).append("': ")
                    .append(String.join("; ", opts))
                    .append(" — reply with the department to pick which one. ");
        }
    }

    /**
     * Structured form of the ambiguous (duplicate-name) travelers from a resolution, for the UI to
     * render as clickable chips. Returns null when there is nothing to disambiguate (so the field is
     * omitted from JSON). The {@code sendText} of each option is a phrase the disambiguation resolver
     * understands (name + department / position), so the UI can send it verbatim as the next turn.
     */
    private List<TripPlanAgentResponse.PendingChoice> buildPendingChoices(
            com.api.bizplay_conversational.model.response.TravelerResolution res) {
        if (res == null || res.getAmbiguous().isEmpty()) {
            return null;
        }
        List<TripPlanAgentResponse.PendingChoice> choices = new ArrayList<>();
        for (com.api.bizplay_conversational.model.response.TravelerResolution.Ambiguous a : res.getAmbiguous()) {
            List<TripPlanAgentResponse.Option> options = new ArrayList<>();
            for (com.api.bizplay_conversational.model.response.StaffLookupResult c : a.getCandidates()) {
                String dept = notBlank(c.getDepartmentName()) ? c.getDepartmentName() : "?";
                String pos = notBlank(c.getPosition()) ? c.getPosition() : "?";
                // Same label the plain-text sentence uses, e.g. "Chan Sokheang (IT / Developer)".
                String label = c.getStaffName() + " (" + dept + " / " + pos + ")";
                // A token the resolver can uniquely match: prefer department, then position. Falls back
                // to the name alone if neither is known (rare; then identical duplicates stay ambiguous).
                String discriminator = notBlank(c.getDepartmentName()) ? c.getDepartmentName()
                        : (notBlank(c.getPosition()) ? c.getPosition() : null);
                String sendText = discriminator != null
                        ? c.getStaffName() + " from " + discriminator
                        : c.getStaffName();
                options.add(TripPlanAgentResponse.Option.builder()
                        .staffId(c.getStaffId())
                        .label(label)
                        .sendText(sendText)
                        .build());
            }
            // Always offer a way out: "Skip" declines this mention without re-triggering lookup.
            options.add(TripPlanAgentResponse.Option.builder()
                    .staffId(null)
                    .label("Skip")
                    .sendText("skip traveler: " + a.getName())
                    .build());
            choices.add(TripPlanAgentResponse.PendingChoice.builder()
                    .kind("STAFF")
                    .name(a.getName())
                    .options(options)
                    .build());
        }
        return choices;
    }

    /** Bare affirmations/approvals that should point the user at the Create button, not the Update Agent. */
    private static final java.util.Set<String> APPROVAL_PHRASES = java.util.Set.of(
            "yes", "y", "ok", "okay", "approve", "approved", "confirm", "confirmed",
            "create", "create it", "create plan", "create the plan", "proceed", "go ahead",
            "done", "looks good", "lgtm", "sure", "yep", "yeah");

    /** True when the whole message is just an approval/affirmation (so it carries no edit to apply). */
    private boolean isApprovalPhrase(String message) {
        if (message == null) {
            return false;
        }
        String m = message.trim().toLowerCase(Locale.ROOT).replaceAll("[.!\\s]+$", "");
        return APPROVAL_PHRASES.contains(m);
    }

    /** Explicit chip command from the UI's "Skip" option: "skip traveler: &lt;name&gt;". */
    private static final java.util.regex.Pattern SKIP_TRAVELER_PATTERN =
            java.util.regex.Pattern.compile("^\\s*skip\\s+traveler\\s*:\\s*(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Negative verbs that, paired with a currently-pending name, mean "don't add this one". */
    private static final java.util.List<String> SKIP_MARKERS = java.util.List.of(
            "don't add", "dont add", "do not add", "not add", "don't include", "dont include",
            "do not include", "exclude", "without", "remove", "skip", "drop");

    /**
     * Detect a request to DROP a still-pending ambiguous traveler, so it is resolved against the
     * pending list instead of re-running staff lookup (which would re-detect the duplicates and loop).
     * Returns the pending name to skip, or null when the message is not a skip/decline for a pending
     * mention. The explicit "skip traveler: X" chip command is always honored; natural negatives
     * ("don't add X", "skip X", "remove X") are honored ONLY when X is currently pending — so a
     * legitimate "remove &lt;added traveler&gt;" still flows to the Update Agent.
     */
    private String detectPendingSkip(String message, ConversationalAgentSession session) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        // 1. Explicit chip command always wins.
        java.util.regex.Matcher m = SKIP_TRAVELER_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String name = m.group(1).trim();
            return name.isEmpty() ? null : name;
        }
        // 2. Natural negative phrasing — only honored when it references a CURRENTLY-pending name.
        List<String> pending = requestBodyBuilderService.pendingTravelerNames(session);
        if (pending.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean negative = false;
        for (String marker : SKIP_MARKERS) {
            if (lower.contains(marker)) {
                negative = true;
                break;
            }
        }
        if (!negative) {
            return null;
        }
        for (String name : pending) {
            if (name != null && lower.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
        return null;
    }

    private String classifyIntent(String message, List<Message> history) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            return classifyIntentFallback(message);
        }

        try {
            String systemPrompt = """
                            Classify the latest user message for a Create Trip Plan Agent.
                            Use the prior conversation for context (e.g. a follow-up that refines an earlier lookup).
                            Return exactly one label:
                            UPDATE - the user wants to CHANGE, CORRECT, REPLACE, or REMOVE something already in the trip plan. Edit verbs: change, update, set, replace, rename, remove, delete, clear, "instead". Examples: "change destination to USA", "set the dates to 2026-07-01..05", "remove Bob Martin", "change John's origin to Busan", "everyone returns via Incheon".
                            TEXT_ANALYSIS - the user is PROVIDING NEW trip plan details to capture: who travels plus trip information such as destination, origin, return point, dates/period, purpose, or transportation method. Examples: "John Doe travels from Seoul to Busan by KTX on 2024-07-01", "add Jane to the Busan trip, departing Seoul", "the trip is to Busan from July 1 to 5".
                            STAFF_LOOKUP - a SIMPLE lookup of ONE specific staff member by name, with no trip details. Examples: "who is John Kim", "find Sarah", "details for employee Lee".
                            DATABASE_LOOKUP - a broader or filtered data QUERY about departments, staff, travelers, or trips: lists, multiple people, a whole department/team, or conditions such as a date, destination, "previous/past trips", position. Also use this to refine such a previous query.
                            OTHER - anything else.
                            Distinguish intent: editing/removing existing plan data -> UPDATE; providing/adding NEW trip details -> TEXT_ANALYSIS; asking to find/list existing data -> STAFF_LOOKUP or DATABASE_LOOKUP.
                            When unsure between STAFF_LOOKUP and DATABASE_LOOKUP, choose DATABASE_LOOKUP.
                            Do not explain. Output only the single label.
                            /no_think
                            """;

            List<Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(systemPrompt));
            if (history != null) {
                prompt.addAll(history);
            }
            prompt.add(new UserMessage(message));

            String content = client.prompt()
                    .messages(prompt)
                    .call()
                    .content();
            // Thinking models (e.g. Qwen3) wrap reasoning in <think>...</think> that enumerates
            // every label. Strip it first; otherwise a plain contains() check below matches a label
            // mentioned in the reasoning (historically always "UPDATE") instead of the real answer.
            String normalized = stripThink(content).toUpperCase(Locale.ROOT);
            // Match each label as a standalone word so "UPDATE" can't match inside "UPDATED" etc.
            // OTHER is intentionally excluded so an unrecognized/OTHER answer falls through to the
            // keyword fallback for a second chance, matching the previous behaviour.
            for (String label : List.of("UPDATE", "TEXT_ANALYSIS", "STAFF_LOOKUP", "DATABASE_LOOKUP")) {
                if (containsWord(normalized, label)) {
                    return label;
                }
            }
            return classifyIntentFallback(message);
        } catch (RuntimeException e) {
            log.warn("Trip plan intent classification failed, using fallback: {}", e.getMessage());
            return classifyIntentFallback(message);
        }
    }

    private String classifyIntentFallback(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        // Edit verbs take precedence: the user is changing/removing existing plan data -> Update.
        if (normalized.contains("change")
                || normalized.contains("update")
                || normalized.contains("replace")
                || normalized.contains("remove")
                || normalized.contains("delete")
                || normalized.contains("rename")
                || normalized.contains("instead")
                || normalized.contains("clear ")
                || normalized.startsWith("set ")) {
            return "UPDATE";
        }
        // Trip detail being PROVIDED (route/dates/method) -> Text Analysis.
        if (normalized.contains(" to ")
                || normalized.contains(" from ")
                || normalized.contains("origin")
                || normalized.contains("destination")
                // Whole-word "depart" family (depart/departs/departing/departure) only -- must NOT
                // match "department", which is a DATABASE_LOOKUP keyword handled further below.
                || containsWord(normalized, "depart(?:s|ing|ed|ure)?")
                || normalized.contains("return")
                || normalized.contains("travel")
                || normalized.contains("transport")
                || normalized.contains("ktx")
                || normalized.contains("flight")
                || normalized.contains("train")
                || normalized.contains("bus")) {
            return "TEXT_ANALYSIS";
        }
        if (normalized.contains("staff")
                || normalized.contains("employee")
                || normalized.contains("department")
                || normalized.contains("traveler")
                || normalized.contains("manager")
                || normalized.contains("position")
                || normalized.contains("team")) {
            return "DATABASE_LOOKUP";
        }
        return "OTHER";
    }

    /** Remove reasoning blocks emitted by thinking models (e.g. Qwen3 &lt;think&gt;...&lt;/think&gt;). */
    private String stripThink(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)</?think>", "");
        return cleaned.trim();
    }

    /** True if {@code wordRegex} occurs in {@code text} as a standalone word (matched at \b boundaries). */
    private boolean containsWord(String text, String wordRegex) {
        return text != null && text.matches("(?s).*\\b(?:" + wordRegex + ")\\b.*");
    }
}
