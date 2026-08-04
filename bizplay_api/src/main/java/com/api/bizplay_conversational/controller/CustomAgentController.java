package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.CustomAgentRequest;
import com.api.bizplay_conversational.model.response.CustomAgentResponse;
import com.api.bizplay_conversational.service.customAgentService.CustomAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * User-defined sub-agents (Phase 1): per-corp CRUD, the built-in read-only tool catalog, and a
 * direct test-run endpoint for the builder UI. Routed automatically in the plan-agent chat.
 */
@Slf4j
@Tag(name = "Conversational Custom Agents", description = "Create per-corp sub-agents with a read-only tool allowlist.")
@RestController
@RequestMapping("/api/v1/agent-conversations/custom-agents")
@RequiredArgsConstructor
public class CustomAgentController {

    private final CustomAgentService customAgentService;
    private final com.api.bizplay_conversational.service.mcpService.McpClientService mcpClientService;

    @Operation(summary = "List one corp's custom agents")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomAgentResponse>>> list(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/agent-conversations/custom-agents - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(customAgentService.list(corpNo)));
    }

    @Operation(summary = "The tool allowlist: built-ins + the corp's MCP-server tools")
    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<Map<String, String>>> tools(
            @RequestParam(value = "corpNo", required = false) String corpNo) {
        Map<String, String> out = new java.util.LinkedHashMap<>(customAgentService.toolCatalog());
        if (corpNo != null && !corpNo.isBlank()) {
            for (var t : mcpClientService.listTools(corpNo)) {
                out.put("mcp:" + t.server() + ":" + t.name(),
                        "[MCP · " + t.server() + (t.trusted() ? "" : " · NOT TRUSTED yet") + "] "
                                + (t.description() == null || t.description().isBlank() ? "external tool" : t.description()));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @Operation(summary = "Create/update a custom agent")
    @PutMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<CustomAgentResponse>> put(@RequestParam("corpNo") String corpNo,
                                                                @PathVariable("name") String name,
                                                                @RequestBody CustomAgentRequest request) {
        log.info("PUT /api/v1/agent-conversations/custom-agents/{} - corpNo={}", name, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(customAgentService.put(corpNo, name, request)));
    }

    @Operation(summary = "Run a custom agent once with a test message")
    @PostMapping("/{name:.+}/test")
    public ResponseEntity<ApiResponse<CustomAgentResponse>> test(@RequestParam("corpNo") String corpNo,
                                                                 @PathVariable("name") String name,
                                                                 @RequestBody CustomAgentRequest request) {
        log.info("POST /api/v1/agent-conversations/custom-agents/{}/test - corpNo={}", name, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(
                customAgentService.test(corpNo, name, request == null ? null : request.getMessage())));
    }

    @Operation(summary = "Delete a custom agent")
    @DeleteMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<String>> delete(@RequestParam("corpNo") String corpNo,
                                                      @PathVariable("name") String name) {
        log.info("DELETE /api/v1/agent-conversations/custom-agents/{} - corpNo={}", name, corpNo);
        customAgentService.delete(corpNo, name);
        return ResponseEntity.ok(ApiResponse.ok("Deleted."));
    }
}
