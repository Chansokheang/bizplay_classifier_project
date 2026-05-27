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

    @Operation(summary = "Create a business trip plan")
    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> create(@Valid @RequestBody PlanCreateRequest request) {
        log.info("POST /api/v1/plans - corpNo={}, planType={}", request.getCorpNo(), request.getPlanType());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(planService.create(request)));
    }
}
