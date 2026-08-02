package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.AgentPromptRequest;
import com.api.bizplay_conversational.model.response.AgentModuleResponse;
import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Turn the Plan agent's optional sub-agent modules on/off per corp. Core modules (guardrail,
 * purpose-segment, field-mapper, form-builder) are fixed ON and cannot be disabled.
 */
@Slf4j
@Tag(name = "Conversational Agent Modules", description = "Enable/disable optional sub-agents per corp.")
@RestController
@RequestMapping("/api/v1/agent-conversations/agent-modules")
@RequiredArgsConstructor
public class AgentModuleController {

    private final AgentPromptService agentPromptService;

    @Operation(summary = "List the Plan agent's modules with their per-corp on/off state")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentModuleResponse>>> list(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/agent-conversations/agent-modules - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(agentPromptService.listModules(corpNo)));
    }

    @Operation(summary = "Enable/disable an optional module for one corp")
    @PutMapping("/{name:.+}")
    public ResponseEntity<ApiResponse<AgentModuleResponse>> set(@RequestParam("corpNo") String corpNo,
                                                                @PathVariable("name") String name,
                                                                @RequestBody AgentPromptRequest request) {
        boolean enabled = request == null || request.getEnabled() == null || request.getEnabled();
        log.info("PUT /api/v1/agent-conversations/agent-modules/{} - corpNo={}, enabled={}", name, corpNo, enabled);
        return ResponseEntity.ok(ApiResponse.ok(agentPromptService.setModule(corpNo, name, enabled)));
    }
}
