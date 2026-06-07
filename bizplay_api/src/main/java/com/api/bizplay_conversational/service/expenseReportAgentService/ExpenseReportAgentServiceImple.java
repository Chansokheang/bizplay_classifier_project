package com.api.bizplay_conversational.service.expenseReportAgentService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.request.ExpenseReportAgentRequest;
import com.api.bizplay_conversational.model.response.ExpenseAnalysisResult;
import com.api.bizplay_conversational.model.response.ExpenseReportAgentResponse;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.ReceiptExtractionResult;
import com.api.bizplay_conversational.model.response.SessionDetailResponse;
import com.api.bizplay_conversational.model.response.SessionSummaryResponse;
import com.api.bizplay_conversational.repository.ConversationalAgentSessionRepo;
import com.api.bizplay_conversational.repository.PlanRepo;
import com.api.bizplay_conversational.service.expenseAnalysisAgentService.ExpenseAnalysisAgentService;
import com.api.bizplay_conversational.service.fileExtractionService.FileExtractionService;
import com.api.bizplay_conversational.service.fileExtractionService.UploadedFile;
import com.api.bizplay_conversational.service.receiptExtractionAgentService.ReceiptExtractionAgentService;
import com.api.bizplay_conversational.service.reportBodyBuilderService.ReportBodyBuilderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ExpenseReportAgentServiceImple implements ExpenseReportAgentService {

    /** Most recent turns (user + assistant) fed back to the model as context. */
    private static final int MAX_HISTORY_TURNS = 20;

    private final ReceiptExtractionAgentService receiptExtractionAgentService;
    private final ExpenseAnalysisAgentService expenseAnalysisAgentService;
    private final ReportBodyBuilderService reportBodyBuilderService;
    private final FileExtractionService fileExtractionService;
    private final ConversationalAgentSessionRepo sessionRepo;
    private final PlanRepo planRepo;
    private final ObjectMapper objectMapper;
    private final Executor agentTaskExecutor;

    public ExpenseReportAgentServiceImple(
            ReceiptExtractionAgentService receiptExtractionAgentService,
            ExpenseAnalysisAgentService expenseAnalysisAgentService,
            ReportBodyBuilderService reportBodyBuilderService,
            FileExtractionService fileExtractionService,
            ConversationalAgentSessionRepo sessionRepo,
            PlanRepo planRepo,
            ObjectMapper objectMapper,
            @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        this.receiptExtractionAgentService = receiptExtractionAgentService;
        this.expenseAnalysisAgentService = expenseAnalysisAgentService;
        this.reportBodyBuilderService = reportBodyBuilderService;
        this.fileExtractionService = fileExtractionService;
        this.sessionRepo = sessionRepo;
        this.planRepo = planRepo;
        this.objectMapper = objectMapper;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> listSessions(String corpNo) {
        return sessionRepo.findByCorpNo(corpNo).stream()
                .filter(s -> ConversationalAgentSession.AgentType.EXPENSE_REPORT.equals(s.getAgentType()))
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
        ConversationalAgentSession s = loadSession(sessionId);
        return SessionDetailResponse.builder()
                .sessionId(s.getId())
                .corpNo(s.getCorpNo())
                .agentType(s.getAgentType() == null ? null : s.getAgentType().name())
                .status(s.getStatus() == null ? null : s.getStatus().name())
                .draftJson(orderedDraft(s))
                .chatEventJson(s.getChatEventJson())
                .createdDate(s.getCreatedDate())
                .updatedDate(s.getUpdatedDate())
                .build();
    }

    @Override
    @Transactional
    public ExpenseReportAgentResponse chat(ExpenseReportAgentRequest request) {
        List<String> fileIds = request.allFileIds();
        if (fileIds.isEmpty()) {
            throw new IllegalArgumentException("At least one receipt fileId is required.");
        }

        ConversationalAgentSession session = resolveSession(request);
        List<Message> history = toMessages(session.getChatEventJson());

        // Resolve uploaded files; only PDF receipts are processed in this version.
        List<UploadedFile> pdfReceipts = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (String fid : fileIds) {
            UploadedFile f = fileExtractionService.get(fid).orElse(null);
            if (f == null) {
                notFound.add(fid);
            } else if (isPdf(f)) {
                pdfReceipts.add(f);
            } else {
                unsupported.add(f.filename() != null ? f.filename() : fid);
            }
        }

        // One pipeline per receipt: Gemma extraction -> qwen3 analysis, all in parallel.
        // The report is receipt-driven: each receipt becomes its own expense line(s).
        int receiptCount = pdfReceipts.size();
        List<String> pipelineFileIds = new ArrayList<>();
        List<CompletableFuture<ExpenseAnalysisResult>> pipelines = new ArrayList<>();
        for (UploadedFile f : pdfReceipts) {
            pipelineFileIds.add(f.fileId());
            pipelines.add(CompletableFuture.supplyAsync(() -> {
                ReceiptExtractionResult ext =
                        receiptExtractionAgentService.extract(request.getCorpNo(), f.content(), f.filename());
                return expenseAnalysisAgentService.analyze(ext, null, history);
            }, agentTaskExecutor));
        }

        CompletableFuture.allOf(pipelines.toArray(new CompletableFuture[0])).join();

        // Merge sequentially (single-threaded) to avoid a read/modify/write race on draft_json.
        int merged = 0;
        for (int i = 0; i < pipelines.size(); i++) {
            merged += reportBodyBuilderService.mergeExpenseAnalysis(
                    session, pipelines.get(i).join(), pipelineFileIds.get(i));
        }

        List<String> subAgents = new ArrayList<>();
        if (receiptCount > 0) {
            subAgents.add("RECEIPT_EXTRACTION_AGENT");
            subAgents.add("EXPENSE_ANALYSIS_AGENT");
        }

        StringBuilder sb = new StringBuilder();
        if (merged > 0) {
            sb.append("Added ").append(merged).append(" expense line(s) to the report. ");
        }
        if (receiptCount > 0) {
            sb.append("Processed ").append(receiptCount).append(" receipt(s). ");
        }
        if (!notFound.isEmpty()) {
            sb.append("Could not find file(s): ").append(String.join(", ", notFound)).append(". ");
        }
        if (!unsupported.isEmpty()) {
            sb.append("Skipped non-PDF file(s) (image OCR not supported yet): ")
                    .append(String.join(", ", unsupported)).append(". ");
        }
        String reply = sb.toString().trim();
        if (reply.isEmpty()) {
            reply = "I could not extract any expense from the uploaded receipt(s). Please attach a valid PDF receipt.";
        }

        String userTurn = "[uploaded receipt(s): " + String.join(", ", fileIds) + "]";
        appendTurn(session, "user", userTurn);
        appendTurn(session, "assistant", reply);
        sessionRepo.save(session);

        // Reload so DB-generated timestamps (created_date / updated_date via NOW()) are returned.
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

        return ExpenseReportAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent("RECEIPT_UPLOAD")
                .delegated(!subAgents.isEmpty())
                .subAgents(subAgents)
                .reply(reply)
                .draftJson(orderedDraft(saved))
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    /**
     * Re-serialize the stored draft through the typed model so the response keys come out in a stable,
     * predictable order. Postgres JSONB does not preserve key order, so reading draft_json straight
     * from the column would return the sections shuffled.
     */
    private JsonNode orderedDraft(ConversationalAgentSession session) {
        return objectMapper.valueToTree(reportBodyBuilderService.snapshot(session));
    }

    // --- session resolution / bootstrap -----------------------------------------

    private ConversationalAgentSession resolveSession(ExpenseReportAgentRequest request) {
        String sessionId = request.getSessionId();

        // No sessionId -> start a new report session, bootstrapped from the referenced trip plan.
        if (sessionId == null || sessionId.isBlank()) {
            if (request.getPlanId() == null || request.getPlanId().isBlank()) {
                throw new IllegalArgumentException("planId is required to start a new expense report session.");
            }
            PlanResponse plan = loadPlan(request.getPlanId());
            if (!request.getCorpNo().equals(plan.getCorpNo())) {
                throw new CustomNotFoundException(
                        "Plan " + request.getPlanId() + " does not belong to corpNo=" + request.getCorpNo());
            }
            // The expense report can only start once the trip plan has been approved.
            assertPlanApproved(plan.getApprovalStatus(), request.getPlanId());
            ConversationalAgentSession created = new ConversationalAgentSession();
            created.setCorpNo(request.getCorpNo());
            created.setAgentType(ConversationalAgentSession.AgentType.EXPENSE_REPORT);
            sessionRepo.save(created); // insert first so the session has an id for the draft link
            reportBodyBuilderService.bootstrapFromPlan(created, plan);
            return created;
        }

        // sessionId provided -> must resolve to an existing EXPENSE_REPORT session for this corp.
        ConversationalAgentSession existing = loadSession(sessionId);
        if (!request.getCorpNo().equals(existing.getCorpNo())
                || !ConversationalAgentSession.AgentType.EXPENSE_REPORT.equals(existing.getAgentType())) {
            throw new CustomNotFoundException(
                    "Session " + sessionId + " does not belong to corpNo=" + request.getCorpNo()
                            + " or is not an EXPENSE_REPORT session.");
        }
        // Re-check on every turn: the linked trip plan must still be approved to keep working on the report.
        UUID planId = tripPlanIdFromDraft(existing);
        if (planId != null) {
            assertPlanApproved(planRepo.findApprovalStatusById(planId), planId.toString());
        }
        return existing;
    }

    /** The approved-plan state required before an expense report may proceed. */
    private static final String APPROVED_STATUS = "Approval complete";

    private void assertPlanApproved(String approvalStatus, String planRef) {
        if (!APPROVED_STATUS.equals(approvalStatus)) {
            throw new IllegalArgumentException(
                    "Trip plan " + planRef + " is not approved (approval_status='" + approvalStatus
                            + "'). The expense report can only proceed once the plan is '" + APPROVED_STATUS + "'.");
        }
    }

    /** Recover the linked trip plan id from the report draft_json (TripPlanId). Null if absent. */
    private UUID tripPlanIdFromDraft(ConversationalAgentSession session) {
        JsonNode draft = session.getDraftJson();
        if (draft != null) {
            JsonNode node = draft.path("TripPlanId");
            if (node.isTextual() && !node.asText().isBlank()) {
                try {
                    return UUID.fromString(node.asText().trim());
                } catch (IllegalArgumentException ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    private PlanResponse loadPlan(String planId) {
        UUID id;
        try {
            id = UUID.fromString(planId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomNotFoundException("Invalid planId: " + planId);
        }
        PlanResponse plan = planRepo.findPlanResponseById(id.toString());
        if (plan == null) {
            throw new CustomNotFoundException("Trip plan not found: " + planId);
        }
        plan.setTravelers(planRepo.findTravelerResponsesByPlanId(id.toString()));
        return plan;
    }

    private ConversationalAgentSession loadSession(String sessionId) {
        UUID id;
        try {
            id = UUID.fromString(sessionId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomNotFoundException("Invalid session id: " + sessionId);
        }
        return sessionRepo.findById(id)
                .orElseThrow(() -> new CustomNotFoundException("Session not found: " + sessionId));
    }

    // --- helpers -----------------------------------------------------------------

    private boolean isPdf(UploadedFile f) {
        String name = f.filename() == null ? "" : f.filename().toLowerCase(Locale.ROOT);
        String ct = f.contentType() == null ? "" : f.contentType().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || ct.contains("pdf");
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
}
