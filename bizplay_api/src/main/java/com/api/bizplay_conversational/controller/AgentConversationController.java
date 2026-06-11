package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.ExpenseReportAgentRequest;
import com.api.bizplay_conversational.model.request.StaffLookupAgentRequest;
import com.api.bizplay_conversational.model.request.TextAnalysisAgentRequest;
import com.api.bizplay_conversational.model.request.TripPlanAgentRequest;
import com.api.bizplay_conversational.model.response.ExpenseReportAgentResponse;
import com.api.bizplay_conversational.model.response.FileUploadResponse;
import com.api.bizplay_conversational.model.response.SessionDetailResponse;
import com.api.bizplay_conversational.model.response.SessionSummaryResponse;
import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.StaffLookupAgentResponse;
import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.api.bizplay_conversational.service.expenseReportAgentService.ExpenseReportAgentService;
import com.api.bizplay_conversational.service.fileExtractionService.FileExtractionService;
import com.api.bizplay_conversational.service.pdfAgentService.PdfAgentService;
import com.api.bizplay_conversational.service.spreadsheetAgentService.SpreadsheetAgentService;
import com.api.bizplay_conversational.service.staffLookupAgentService.StaffLookupAgentService;
import com.api.bizplay_conversational.service.textAnalysisAgentService.TextAnalysisAgentService;
import com.api.bizplay_conversational.service.fileExtractionService.UploadedFile;
import com.api.bizplay_conversational.service.tripPlanAgentService.TripPlanAgentService;
import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Tag(name = "Agent Conversations", description = "Manage conversational agent sessions")
@RestController
@RequestMapping("/api/v1/agent-conversations")
@RequiredArgsConstructor
public class AgentConversationController {

    private final StaffLookupAgentService staffLookupAgentService;
    private final TextAnalysisAgentService textAnalysisAgentService;
    private final SpreadsheetAgentService spreadsheetAgentService;
    private final PdfAgentService pdfAgentService;
    private final TripPlanAgentService tripPlanAgentService;
    private final ExpenseReportAgentService expenseReportAgentService;
    private final FileExtractionService fileExtractionService;

    @PostMapping("/sub-agents/staff-lookup")
    public ResponseEntity<ApiResponse<StaffLookupAgentResponse>> lookupStaff(
            @Valid @RequestBody StaffLookupAgentRequest request) {
//        log.info("POST /api/v1/agent-conversations/staff-lookup - corpNo={}", request.getCorpNo());
        return ResponseEntity.ok(ApiResponse.ok(staffLookupAgentService.lookup(request)));
    }

    @PostMapping("/sub-agents/text-analysis")
    public ResponseEntity<ApiResponse<TextAnalysisResult>> analyzeText(
            @Valid @RequestBody TextAnalysisAgentRequest request) {
        log.info("POST /api/v1/agent-conversations/text-analysis");
        return ResponseEntity.ok(ApiResponse.ok(textAnalysisAgentService.analyze(request.getMessage())));
    }

    /**
     * Test the Spreadsheet sub-agent in isolation: upload a staff-list spreadsheet and get the
     * extracted/resolved data back directly (no session, no draft merge).
     */
    @PostMapping(value = "/sub-agents/spreadsheet", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<SpreadsheetAnalysisResult>> analyzeSpreadsheet(
            @RequestParam("corpNo") String corpNo,
            @RequestPart("file") MultipartFile file) throws IOException {
        log.info("POST /api/v1/agent-conversations/sub-agents/spreadsheet - corpNo={}, file={}",
                corpNo, file == null ? null : file.getOriginalFilename());
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty.");
        }
        SpreadsheetAnalysisResult result =
                spreadsheetAgentService.analyze(corpNo, file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Test the PDF sub-agent in isolation: upload a trip document (booking confirmation, invitation,
     * itinerary) and get the extracted trip info back directly (no session, no draft merge).
     */
    @PostMapping(value = "/sub-agents/pdf", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<TextAnalysisResult>> analyzePdf(
            @RequestParam("corpNo") String corpNo,
            @RequestPart("file") MultipartFile file) throws IOException {
        log.info("POST /api/v1/agent-conversations/sub-agents/pdf - corpNo={}, file={}",
                corpNo, file == null ? null : file.getOriginalFilename());
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty.");
        }
        TextAnalysisResult result =
                pdfAgentService.analyze(corpNo, file.getBytes(), file.getOriginalFilename(), List.of());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Download a previously-uploaded file (a plan/report attachment) by its fileId. Streams the raw
     * bytes with the original filename and content type. 404 if the fileId is unknown.
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable("fileId") String fileId) {
        log.info("GET /api/v1/agent-conversations/files/{}/download", fileId);
        UploadedFile file = fileExtractionService.get(fileId)
                .orElseThrow(() -> new CustomNotFoundException("File not found: " + fileId));

        String filename = file.filename() != null && !file.filename().isBlank() ? file.filename() : fileId;
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.contentType() != null && !file.contentType().isBlank()) {
            try {
                contentType = MediaType.parseMediaType(file.contentType());
            } catch (RuntimeException ignored) {
                // keep octet-stream for an unparseable stored content type
            }
        }
        byte[] content = file.content() != null ? file.content() : new byte[0];
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(content.length)
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    /**
     * Upload a file (e.g. a staff-list spreadsheet) and get a fileId back. Pass that fileId in a
     * subsequent /trip-plan request to trigger the Spreadsheet Agent.
     */
    @PostMapping(value = "/files/create", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestPart("file") MultipartFile file) throws IOException {
        log.info("POST /api/v1/agent-conversations/files - name={}, bytes={}",
                file == null ? null : file.getOriginalFilename(), file == null ? 0 : file.getSize());
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty.");
        }
        String fileId = fileExtractionService.store(
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return ResponseEntity.ok(ApiResponse.ok(FileUploadResponse.builder()
                .fileId(fileId)
                .filename(file.getOriginalFilename())
                .size(file.getSize())
                .build()));
    }

    /**
     * Upload several files at once (e.g. a staff spreadsheet plus trip PDFs) and get back one
     * fileId per file. Pass the returned ids as {@code fileIds} in a subsequent /trip-plan request.
     */
    @PostMapping(value = "/files/create-batch", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> uploadFiles(
            @RequestPart("files") List<MultipartFile> files) throws IOException {
        log.info("POST /api/v1/agent-conversations/files/create-batch - count={}",
                files == null ? 0 : files.size());
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required.");
        }
        List<FileUploadResponse> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fileId = fileExtractionService.store(
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
            uploaded.add(FileUploadResponse.builder()
                    .fileId(fileId)
                    .filename(file.getOriginalFilename())
                    .size(file.getSize())
                    .build());
        }
        if (uploaded.isEmpty()) {
            throw new IllegalArgumentException("All provided files were empty.");
        }
        return ResponseEntity.ok(ApiResponse.ok(uploaded));
    }

    /** List existing sessions for a corp (newest first) so a sessionId can be found and reused. */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionSummaryResponse>>> listSessions(
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/agent-conversations/sessions - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(tripPlanAgentService.listSessions(corpNo)));
    }

    /** Get a single session's full detail (draft + chat history) by id. */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<SessionDetailResponse>> getSession(
            @PathVariable("sessionId") String sessionId) {
        log.info("GET /api/v1/agent-conversations/sessions/{}", sessionId);
        return ResponseEntity.ok(ApiResponse.ok(tripPlanAgentService.getSession(sessionId)));
    }

    /**
     * Save (overwrite) a session's draft so work can be paused and resumed later. The body is the
     * edited draft object (same shape as {@code draftJson} returned by GET /sessions/{id}). The saved
     * draft is re-normalized and re-validated, and the session status is refreshed.
     */
    @PutMapping("/sessions/{sessionId}/draft")
    public ResponseEntity<ApiResponse<SessionDetailResponse>> saveDraft(
            @PathVariable("sessionId") String sessionId,
            @RequestBody JsonNode draftJson) {
        log.info("PUT /api/v1/agent-conversations/sessions/{}/draft", sessionId);
        return ResponseEntity.ok(ApiResponse.ok(tripPlanAgentService.saveDraft(sessionId, draftJson)));
    }

    @PostMapping("/agents/trip-plan")
    public ResponseEntity<ApiResponse<TripPlanAgentResponse>> chatTripPlanAgent(
            @Valid @RequestBody TripPlanAgentRequest request) {
//        log.info("POST /api/v1/agent-conversations/trip-plan - corpNo={}", request.getCorpNo());
        return ResponseEntity.ok(ApiResponse.ok(tripPlanAgentService.chat(request)));
    }

    // --- Expense Report ("After Business Trip") Agent --------------------------------

    /**
     * One Expense Report Agent turn. Start a new session by passing {@code planId} (the finished trip
     * plan to report on); the draft is bootstrapped from it. Continue with {@code sessionId}. Attach
     * PDF receipts via {@code fileIds} and/or describe the expense in {@code message}.
     */
    @PostMapping("/agents/expense-report")
    public ResponseEntity<ApiResponse<ExpenseReportAgentResponse>> chatExpenseReportAgent(
            @Valid @RequestBody ExpenseReportAgentRequest request) {
        log.info("POST /api/v1/agent-conversations/agents/expense-report - corpNo={}, planId={}, sessionId={}",
                request.getCorpNo(), request.getPlanId(), request.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok(expenseReportAgentService.chat(request)));
    }

    /** List EXPENSE_REPORT sessions for a corp (newest first). */
    @GetMapping("/agents/expense-report/sessions")
    public ResponseEntity<ApiResponse<List<SessionSummaryResponse>>> listExpenseReportSessions(
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/agent-conversations/agents/expense-report/sessions - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(expenseReportAgentService.listSessions(corpNo)));
    }

    /** Get a single expense-report session's full detail (draft + chat history) by id. */
    @GetMapping("/agents/expense-report/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<SessionDetailResponse>> getExpenseReportSession(
            @PathVariable("sessionId") String sessionId) {
        log.info("GET /api/v1/agent-conversations/agents/expense-report/sessions/{}", sessionId);
        return ResponseEntity.ok(ApiResponse.ok(expenseReportAgentService.getSession(sessionId)));
    }
}
