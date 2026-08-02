package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.AgentPromptRequest;
import com.api.bizplay_conversational.model.response.AgentPromptResponse;
import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;
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

/**
 * Customize sub-agent system prompts (and the chat "starter-message") at runtime. Every agent keeps
 * its compiled-in default — a stored, enabled row overrides it; DELETE restores the default.
 */
@Slf4j
@Tag(name = "Conversational Agent Prompts", description = "Customize sub-agent prompts with built-in defaults.")
@RestController
@RequestMapping("/api/v1/agent-conversations/agent-prompts")
@RequiredArgsConstructor
public class AgentPromptController {

    private final AgentPromptService agentPromptService;

    @Operation(summary = "List every agent prompt for one corp (default + custom + which is live)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentPromptResponse>>> list(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/agent-conversations/agent-prompts - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(agentPromptService.list(corpNo)));
    }

    @Operation(summary = "Get one agent's prompt state for one corp")
    @GetMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<AgentPromptResponse>> get(@RequestParam("corpNo") String corpNo,
                                                                @PathVariable("name") String name) {
        log.info("GET /api/v1/agent-conversations/agent-prompts/{} - corpNo={}", name, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(agentPromptService.get(corpNo, name)));
    }

    @Operation(summary = "Create/update a corp's custom prompt (name 'starter-message' = the chat opener)")
    @PutMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<AgentPromptResponse>> put(@RequestParam("corpNo") String corpNo,
                                                                @PathVariable("name") String name,
                                                                @RequestBody AgentPromptRequest request) {
        log.info("PUT /api/v1/agent-conversations/agent-prompts/{} - corpNo={}", name, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(agentPromptService.put(corpNo, name, request)));
    }

    /** POST alias of PUT — "create" reads naturally for first-time customization. */
    @Operation(summary = "Create a corp's custom prompt (alias of PUT)")
    @PostMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<AgentPromptResponse>> create(@RequestParam("corpNo") String corpNo,
                                                                   @PathVariable("name") String name,
                                                                   @RequestBody AgentPromptRequest request) {
        log.info("POST /api/v1/agent-conversations/agent-prompts/{} - corpNo={}", name, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(agentPromptService.put(corpNo, name, request)));
    }

    @Operation(summary = "Reset a corp's prompt to the built-in default")
    @DeleteMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<String>> reset(@RequestParam("corpNo") String corpNo,
                                                     @PathVariable("name") String name) {
        log.info("DELETE /api/v1/agent-conversations/agent-prompts/{} - corpNo={}", name, corpNo);
        agentPromptService.reset(corpNo, name);
        return ResponseEntity.ok(ApiResponse.ok("Reset to default."));
    }
}
