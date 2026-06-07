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
        String intent;
        if (hasFiles) {
            intent = "FILE_UPLOAD";
        } else if (isApprovalPhrase(request.getMessage())) {
            intent = "APPROVE";
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

            // Text (the message) is the priority source — merged first and authoritatively.
            TextAnalysisResult msgAnalysis = msgFuture.join();
            if (msgAnalysis != null) {
                requestBodyBuilderService.mergeTextAnalysis(session, msgAnalysis, true);
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
                    requestBodyBuilderService.mergeTextAnalysis(session, pdf, msgAnalysis == null);
                } else {
                    log.info("PDF '{}' describes a different trip; taking its travelers only.", f.filename());
                    requestBodyBuilderService.mergeTravelersOnly(session, pdf);
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
            requestBodyBuilderService.mergeTextAnalysis(session, analysis);

            reply = "I updated the trip plan draft from your message"
                    + (staffMatched ? " (staff: " + staffLookup.getResult().getStaffName() + ")" : "")
                    + ".";
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
            if (!applied.isEmpty()) {
                draftModified = true;
                reply = "Applied " + applied.size() + " update(s): " + String.join("; ", applied) + ".";
            } else {
                reply = "I couldn't find a valid change to make from that. Could you rephrase what to update?";
            }
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

        response.delegated(delegated).subAgents(subAgents).reply(reply);

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

    private String classifyIntent(String message, List<Message> history) {
        ChatClient client = chatClientRegistry.get(modelName);
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
                            Do not explain.
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
            String normalized = content == null ? "" : content.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("UPDATE")) {
                return "UPDATE";
            }
            if (normalized.contains("TEXT_ANALYSIS")) {
                return "TEXT_ANALYSIS";
            }
            if (normalized.contains("STAFF_LOOKUP")) {
                return "STAFF_LOOKUP";
            }
            if (normalized.contains("DATABASE_LOOKUP")) {
                return "DATABASE_LOOKUP";
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
                || normalized.contains("depart")
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
}
