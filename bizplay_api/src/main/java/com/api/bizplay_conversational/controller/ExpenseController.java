package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.ReportApprovalStatusRequest;
import com.api.bizplay_conversational.model.request.ReportBatchDeleteRequest;
import com.api.bizplay_conversational.model.request.ReportCreateRequest;
import com.api.bizplay_conversational.model.response.ReportBatchDeleteResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "Business Trip Reports", description = "Create and manage business trip expense reports")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /** Get all reports for a corp (each header with its expense lines). */
    @Operation(summary = "Get all reports by corpNo")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getByCorpNo(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/reports - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getByCorpNo(corpNo)));
    }

    /** Get a full report (header + all expense lines) by its id. */
    @Operation(summary = "Get a report by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReportResponse>> getById(@PathVariable("id") String id) {
        log.info("GET /api/v1/reports/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getById(id)));
    }

    /** Create a report (header + expense lines) from an approved trip plan's draft. */
    @Operation(summary = "Create a report")
    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> create(@Valid @RequestBody ReportCreateRequest request) {
        log.info("POST /api/v1/reports - corpNo={}, tripPlanId={}, sessionId={}",
                request.getCorpNo(), request.getTripPlanId(), request.getAgentSessionId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(expenseService.create(request)));
    }

    /** Replace an existing report's content (all expense lines + header approval fields) by id. */
    @Operation(summary = "Update a report by id")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReportResponse>> update(
            @PathVariable("id") String id,
            @Valid @RequestBody ReportCreateRequest request) {
        log.info("PUT /api/v1/reports/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(expenseService.update(id, request)));
    }

    /** Update only the approval_status of a report, by its id in the body. */
    @Operation(summary = "Update a report's approval_status by id")
    @PatchMapping("/approval_status")
    public ResponseEntity<ApiResponse<ReportResponse>> updateApprovalStatus(
            @Valid @RequestBody ReportApprovalStatusRequest request) {
        log.info("PATCH /api/v1/reports/approval_status - id={}, approvalStatus={}",
                request.getId(), request.getApprovalStatus());
        return ResponseEntity.ok(
                ApiResponse.ok(expenseService.updateApprovalStatus(request.getId(), request.getApprovalStatus())));
    }

    /** Delete several reports by id (each with its lines, expenses, attachments). */
    @Operation(summary = "Delete multiple reports by id")
    @DeleteMapping("/batch")
    public ResponseEntity<ApiResponse<ReportBatchDeleteResponse>> deleteBatch(
            @Valid @RequestBody ReportBatchDeleteRequest request) {
        log.info("DELETE /api/v1/reports/batch - {} id(s)", request.getIds() == null ? 0 : request.getIds().size());
        return ResponseEntity.ok(ApiResponse.ok(expenseService.deleteByIds(request.getIds())));
    }

    /** Delete one report (header + its lines, expenses, attachments) by id. */
    @Operation(summary = "Delete a report by id")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteById(@PathVariable("id") String id) {
        log.info("DELETE /api/v1/reports/{}", id);
        expenseService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted report " + id));
    }
}
