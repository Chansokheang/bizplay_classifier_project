package com.api.bizplay_conversational.controller;

import com.api.bizplay_conversational.model.request.PlanApprovalStatusRequest;
import com.api.bizplay_conversational.model.request.PlanBatchDeleteRequest;
import com.api.bizplay_conversational.model.request.PlanCreateRequest;
import com.api.bizplay_conversational.model.response.PlanBatchDeleteResponse;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.service.planService.PlanService;
import com.api.bizplay_chatbot.common.dto.ApiResponse;
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
@Tag(name = "Business Trip Plans", description = "Create and manage conversational business trip plans")
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /**
     * Create a business trip plan from a full body (the conversational draft_json), including the
     * originating {@code sessionId} (alias {@code AgentSessionId}) so the plan links back to its session.
     */
    @Operation(summary = "Get all business trip plans by corpNo")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanResponse>>> getByCorpNo(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/plans - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(planService.getByCorpNo(corpNo)));
    }

    @Operation(summary = "Get all business trip plans by approval_status (within a corp)")
    @GetMapping("/by-approval-status")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> getByApprovalStatus(
            @RequestParam("corpNo") String corpNo,
            @RequestParam("approvalStatus") String approvalStatus) {
        log.info("GET /api/v1/plans/by-approval-status - corpNo={}, approvalStatus={}", corpNo, approvalStatus);
        return ResponseEntity.ok(ApiResponse.ok(planService.getByApprovalStatus(corpNo, approvalStatus)));
    }

    @Operation(summary = "Get a business trip plan by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanResponse>> getById(@PathVariable("id") String id) {
        log.info("GET /api/v1/plans/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(planService.getById(id)));
    }

    @Operation(summary = "Create a business trip plan")
    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> create(@Valid @RequestBody PlanCreateRequest request) {
        log.info("POST /api/v1/plans - corpNo={}, planType={}, sessionId={}",
                request.getCorpNo(), request.getPlanType(), request.getAgentSessionId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(planService.create(request)));
    }

    /**
     * Update the existing plan linked to the body's {@code sessionId} (alias {@code AgentSessionId}):
     * re-writes conversational_trip_plan and fully replaces its travelers and attachments.
     */
    @Operation(summary = "Update a business trip plan by sessionId")
    @PutMapping
    public ResponseEntity<ApiResponse<PlanResponse>> update(@Valid @RequestBody PlanCreateRequest request) {
        log.info("PUT /api/v1/plans - sessionId={}, corpNo={}", request.getAgentSessionId(), request.getCorpNo());
        return ResponseEntity.ok(ApiResponse.ok(planService.update(request)));
    }

    /** Update only the approval_status of a plan, identified by its id in the body. */
    @Operation(summary = "Update a plan's approval_status by id")
    @PatchMapping("/approval_status")
    public ResponseEntity<ApiResponse<PlanResponse>> updateApprovalStatus(
            @Valid @RequestBody PlanApprovalStatusRequest request) {
        log.info("PATCH /api/v1/plans/approval_status - id={}, approvalStatus={}",
                request.getId(), request.getApprovalStatus());
        return ResponseEntity.ok(
                ApiResponse.ok(planService.updateApprovalStatus(request.getId(), request.getApprovalStatus())));
    }

    /** Delete several business trip plans (and their travelers + attachments) by id. */
    @Operation(summary = "Delete multiple business trip plans by id")
    @DeleteMapping("/batch")
    public ResponseEntity<ApiResponse<PlanBatchDeleteResponse>> deleteBatch(
            @Valid @RequestBody PlanBatchDeleteRequest request) {
        log.info("DELETE /api/v1/plans/batch - {} id(s)", request.getIds() == null ? 0 : request.getIds().size());
        return ResponseEntity.ok(ApiResponse.ok(planService.deleteByIds(request.getIds())));
    }

    /** Delete a business trip plan (and its travelers + attachments) by id. */
    @Operation(summary = "Delete a business trip plan by id")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteById(@PathVariable("id") String id) {
        log.info("DELETE /api/v1/plans/{}", id);
        planService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted plan " + id));
    }
}
