package com.api.bizplay_conversational.controller;

import com.api.bizplay_conversational.model.request.PlanCreateRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
