package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.LlmSettingsUpdateRequest;
import com.api.bizplay_conversational.model.response.LlmSettingsResponse;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Tag(name = "Conversational LLM Settings",
        description = "Choose which LLM the conversational sub-agents use, at runtime.")
@RestController
@RequestMapping("/api/v1/agent-conversations/llm-settings")
@RequiredArgsConstructor
public class LlmSettingsController {

    private final LlmSettingsService llmSettingsService;

    /**
     * Close the model picker to remote callers. FALSE by default, so the demo's picker switches
     * models with nothing to type - which is the whole point of having it in the composer. Set
     * {@code LLM_SETTINGS_LOCKED=true} on a deployment where only the host may repoint the agents.
     */
    @Value("${app.conversational.llm-settings.locked:false}")
    private boolean locked;

    @Operation(summary = "Get the active conversational LLM and the selectable models")
    @GetMapping
    public ResponseEntity<ApiResponse<LlmSettingsResponse>> get() {
        log.info("GET /api/v1/agent-conversations/llm-settings");
        return ResponseEntity.ok(ApiResponse.ok(llmSettingsService.getSettings()));
    }

    /**
     * Set the active model (an empty model clears the override and each agent falls back to its
     * own default).
     * <p>
     * This route was disabled on 2026-09-29 for being reachable from the internet unauthenticated,
     * then gated behind a shared token. The token was dropped on 2026-10-15: every other endpoint
     * in this demo is open, the setting is not a secret, and typing a token to change a dropdown
     * made the picker unusable in practice. {@code LLM_SETTINGS_LOCKED=true} brings the gate back
     * for a deployment that needs it.
     */
    @Operation(summary = "Set the active conversational LLM (empty model clears the override). "
            + "Open unless the deployment sets LLM_SETTINGS_LOCKED=true, which limits it to the host.")
    @PutMapping
    public ResponseEntity<ApiResponse<LlmSettingsResponse>> update(
            @RequestBody LlmSettingsUpdateRequest request, HttpServletRequest http) {
        if (locked && !isLoopback(http.getRemoteAddr())) {
            log.warn("PUT /api/v1/agent-conversations/llm-settings REFUSED for {} - "
                    + "LLM_SETTINGS_LOCKED=true limits model changes to this machine",
                    http.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    "This deployment only accepts model changes from the server itself."));
        }
        log.info("PUT /api/v1/agent-conversations/llm-settings - model={}", request.getModel());
        return ResponseEntity.ok(ApiResponse.ok(llmSettingsService.setActiveModel(request.getModel())));
    }

    private boolean isLoopback(String addr) {
        if (addr == null || addr.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(addr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
