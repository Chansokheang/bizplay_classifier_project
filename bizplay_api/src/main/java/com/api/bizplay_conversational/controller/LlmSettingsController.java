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
import org.springframework.web.bind.annotation.RequestHeader;
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
     * Shared secret that lets a NON-local caller change the active model. Unset by default, which
     * means remote callers are refused outright and only a request from the machine running the
     * app can switch models.
     */
    @Value("${app.conversational.llm-settings.admin-token:}")
    private String adminToken;

    @Operation(summary = "Get the active conversational LLM and the selectable models")
    @GetMapping
    public ResponseEntity<ApiResponse<LlmSettingsResponse>> get() {
        log.info("GET /api/v1/agent-conversations/llm-settings");
        return ResponseEntity.ok(ApiResponse.ok(llmSettingsService.getSettings()));
    }

    /**
     * Set the active model.
     * <p>
     * This route was DISABLED on 2026-09-29 because it was reachable from the internet with no
     * authentication at all - anyone could point every conversational agent at a model of their
     * choosing. It is enabled again here, but gated: the caller must either be on the machine
     * running the app (loopback) or present {@code X-Admin-Token} matching
     * {@code app.conversational.llm-settings.admin-token}. That property is EMPTY by default, so
     * an internet-facing deployment stays closed until someone deliberately sets it. The demo UI
     * sends no token, which is what makes the picker work locally and refuse in production.
     */
    @Operation(summary = "Set the active conversational LLM (empty model clears the override). "
            + "Local callers only, unless X-Admin-Token is configured and supplied.")
    @PutMapping
    public ResponseEntity<ApiResponse<LlmSettingsResponse>> update(
            @RequestBody LlmSettingsUpdateRequest request,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            HttpServletRequest http) {
        if (!mayChangeModel(http, token)) {
            log.warn("PUT /api/v1/agent-conversations/llm-settings REFUSED for {} - not a local "
                    + "caller and no valid X-Admin-Token", http.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.ok(null));
        }
        log.info("PUT /api/v1/agent-conversations/llm-settings - model={}", request.getModel());
        return ResponseEntity.ok(ApiResponse.ok(llmSettingsService.setActiveModel(request.getModel())));
    }

    /** Loopback caller, or the configured admin token. Never both-optional: an unset token denies. */
    private boolean mayChangeModel(HttpServletRequest http, String token) {
        if (adminToken != null && !adminToken.isBlank() && adminToken.equals(token)) {
            return true;
        }
        return isLoopback(http.getRemoteAddr());
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
