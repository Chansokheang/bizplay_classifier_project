package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.LlmSettingsUpdateRequest;
import com.api.bizplay_conversational.model.response.LlmSettingsResponse;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Conversational LLM Settings",
        description = "Choose which LLM the conversational sub-agents use, at runtime.")
@RestController
@RequestMapping("/api/v1/agent-conversations/llm-settings")
@RequiredArgsConstructor
public class LlmSettingsController {

    private final LlmSettingsService llmSettingsService;

    @Operation(summary = "Get the active conversational LLM and the selectable models")
    @GetMapping
    public ResponseEntity<ApiResponse<LlmSettingsResponse>> get() {
        log.info("GET /api/v1/agent-conversations/llm-settings");
        return ResponseEntity.ok(ApiResponse.ok(llmSettingsService.getSettings()));
    }

    @Operation(summary = "Set the active conversational LLM (empty model clears the override)")
    @PutMapping
    public ResponseEntity<ApiResponse<LlmSettingsResponse>> update(
            @RequestBody LlmSettingsUpdateRequest request) {
        log.info("PUT /api/v1/agent-conversations/llm-settings - model={}", request.getModel());
        return ResponseEntity.ok(ApiResponse.ok(llmSettingsService.setActiveModel(request.getModel())));
    }
}
