package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.entity.ConversationalMcpServer;
import com.api.bizplay_conversational.service.mcpService.McpClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
import java.util.stream.Collectors;

/**
 * Corp-registered MCP servers: CRUD + connection test. Tools of enabled servers surface in the
 * custom-agent builder as {@code mcp:<server>:<tool>}; calls run only for TRUSTED servers.
 */
@Slf4j
@Tag(name = "Conversational MCP Servers", description = "Connect a corp's MCP servers as custom-agent tools.")
@RestController
@RequestMapping("/api/v1/agent-conversations/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpClientService mcpClientService;

    @Getter
    @Setter
    public static class McpServerRequest {
        private String url;
        private String authHeader;
        private Boolean trusted;
        private Boolean enabled;
    }

    @Operation(summary = "List one corp's MCP servers")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/agent-conversations/mcp-servers - corpNo={}", corpNo);
        List<Map<String, Object>> out = mcpClientService.list(corpNo).stream()
                .map(McpServerController::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @Operation(summary = "Register/update an MCP server (URL is SSRF-guarded)")
    @PutMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> put(@RequestParam("corpNo") String corpNo,
                                                                @PathVariable("name") String name,
                                                                @RequestBody McpServerRequest request) {
        log.info("PUT /api/v1/agent-conversations/mcp-servers/{} - corpNo={}", name, corpNo);
        ConversationalMcpServer s = mcpClientService.put(corpNo, name,
                request == null ? null : request.getUrl(),
                request == null ? null : request.getAuthHeader(),
                request == null ? null : request.getTrusted(),
                request == null ? null : request.getEnabled());
        return ResponseEntity.ok(ApiResponse.ok(toMap(s)));
    }

    @Operation(summary = "Connection test: MCP initialize + tools/list")
    @PostMapping("/{name:.+}/test")
    public ResponseEntity<ApiResponse<McpClientService.TestResult>> test(@RequestParam("corpNo") String corpNo,
                                                                         @PathVariable("name") String name) {
        log.info("POST /api/v1/agent-conversations/mcp-servers/{}/test - corpNo={}", name, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(mcpClientService.test(corpNo, name)));
    }

    @Operation(summary = "Remove an MCP server")
    @DeleteMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<String>> delete(@RequestParam("corpNo") String corpNo,
                                                      @PathVariable("name") String name) {
        log.info("DELETE /api/v1/agent-conversations/mcp-servers/{} - corpNo={}", name, corpNo);
        mcpClientService.delete(corpNo, name);
        return ResponseEntity.ok(ApiResponse.ok("Deleted."));
    }

    private static Map<String, Object> toMap(ConversationalMcpServer s) {
        return Map.of(
                "name", s.getName(),
                "url", s.getUrl(),
                "hasAuth", s.getAuthHeader() != null,
                "trusted", s.isTrusted(),
                "enabled", s.isEnabled());
    }
}
