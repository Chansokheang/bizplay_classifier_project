package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.ReportBatchDeleteRequest;
import com.api.bizplay_conversational.model.request.ReportCreateRequest;
import com.api.bizplay_conversational.model.response.ReportBatchDeleteResponse;
import com.api.bizplay_conversational.model.response.ReportLineResponse;
import com.api.bizplay_conversational.model.response.ReportResponse;
import com.api.bizplay_conversational.service.expenseService.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "Business Trip Reports", description = "Create the actual business trip expense report from an approved draft")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /** Get all expense-report lines for a corp (each with its linked expense). */
    @Operation(summary = "Get all expense report lines by corpNo")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReportLineResponse>>> getByCorpNo(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/reports - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getByCorpNo(corpNo)));
    }

    /** Get a single expense-report line (conversational_trip_report) by its id, with its expense. */
    @Operation(summary = "Get an expense report line by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReportLineResponse>> getById(@PathVariable("id") String id) {
        log.info("GET /api/v1/reports/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getById(id)));
    }

    /** Delete several expense-report lines by id (each with its linked expense + attachments). */
    @Operation(summary = "Delete multiple expense report lines by id")
    @DeleteMapping("/batch")
    public ResponseEntity<ApiResponse<ReportBatchDeleteResponse>> deleteBatch(
            @Valid @RequestBody ReportBatchDeleteRequest request) {
        log.info("DELETE /api/v1/reports/batch - {} id(s)", request.getIds() == null ? 0 : request.getIds().size());
        return ResponseEntity.ok(ApiResponse.ok(expenseService.deleteByIds(request.getIds())));
    }

    /** Delete one expense-report line (and its linked expense + attachments) by id. */
    @Operation(summary = "Delete an expense report line by id")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteById(@PathVariable("id") String id) {
        log.info("DELETE /api/v1/reports/{}", id);
        expenseService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted report line " + id));
    }

    /**
     * Create a business trip report from the approved conversational draft (the report body, including
     * its expense sections and the trip plan / session it links to). Normalizes the expense sections
     * into the report tables and marks the drafting session POSTED.
     */
    @Operation(summary = "Create a business trip report")
    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> create(@Valid @RequestBody ReportCreateRequest request) {
        log.info("POST /api/v1/reports - corpNo={}, tripPlanId={}, sessionId={}",
                request.getCorpNo(), request.getTripPlanId(), request.getAgentSessionId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(expenseService.create(request)));
    }
}
