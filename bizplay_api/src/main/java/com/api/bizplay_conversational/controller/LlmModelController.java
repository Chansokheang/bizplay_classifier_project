package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.LlmModelRequest;
import com.api.bizplay_conversational.model.response.LlmModelResponse;
import com.api.bizplay_conversational.model.response.LlmModelTestResponse;
import com.api.bizplay_conversational.service.llmSettingsService.LlmModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manage the LLM models the conversational agents can use — add/update/delete DB-backed models (with
 * their API key), which are hot-registered into the live ChatClient registry. Static app.llm.models
 * config entries are listed too, but are read-only (source = CONFIG).
 */
@Slf4j
@Tag(name = "Conversational LLM Models", description = "Add, update, and delete LLM models at runtime.")
@RestController
@RequestMapping("/api/v1/agent-conversations/llm-models")
@RequiredArgsConstructor
public class LlmModelController {

    private final LlmModelService llmModelService;

    @Operation(summary = "List all LLM models (config + DB-managed)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<LlmModelResponse>>> list() {
        log.info("GET /api/v1/agent-conversations/llm-models");
        return ResponseEntity.ok(ApiResponse.ok(llmModelService.list()));
    }

    @Operation(summary = "Get one LLM model by name")
    @GetMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<LlmModelResponse>> get(@PathVariable("name") String name) {
        log.info("GET /api/v1/agent-conversations/llm-models/{}", name);
        return ResponseEntity.ok(ApiResponse.ok(llmModelService.getByName(name)));
    }

    @Operation(summary = "Add a new LLM model (with its API key) and register it")
    @PostMapping
    public ResponseEntity<ApiResponse<LlmModelResponse>> create(@RequestBody LlmModelRequest request) {
        log.info("POST /api/v1/agent-conversations/llm-models - name={}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(llmModelService.create(request)));
    }

    @Operation(summary = "Update a DB-managed LLM model (null apiKey keeps the stored key)")
    @PutMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<LlmModelResponse>> update(
            @PathVariable("name") String name, @RequestBody LlmModelRequest request) {
        log.info("PUT /api/v1/agent-conversations/llm-models/{}", name);
        return ResponseEntity.ok(ApiResponse.ok(llmModelService.update(name, request)));
    }

    @Operation(summary = "Test a model's connectivity — sends a tiny ping and reports ok/error")
    @PostMapping("/{name:.+}/test")
    public ResponseEntity<ApiResponse<LlmModelTestResponse>> test(@PathVariable("name") String name) {
        log.info("POST /api/v1/agent-conversations/llm-models/{}/test", name);
        return ResponseEntity.ok(ApiResponse.ok(llmModelService.test(name)));
    }

    @Operation(summary = "Delete a DB-managed LLM model and unregister it")
    @DeleteMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable("name") String name) {
        log.info("DELETE /api/v1/agent-conversations/llm-models/{}", name);
        llmModelService.delete(name);
        return ResponseEntity.ok(ApiResponse.ok("Deleted LLM model " + name));
    }
}
