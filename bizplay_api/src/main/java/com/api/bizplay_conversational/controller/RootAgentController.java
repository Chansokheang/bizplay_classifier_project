package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.service.rootAgentService.RootAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One chat for the whole trip. The root agent decides which specialist answers each turn — plan,
 * settlement or booking — and holds a session per child so the user can move between them and come
 * back to find their work still there.
 *
 * <p>Purely additive: the three agents are untouched and their own endpoints keep working exactly
 * as before. This is another way in, not a replacement.
 */
@Slf4j
@Tag(name = "Root Agent", description = "One conversation that routes between the plan, settlement and booking agents.")
@RestController
@RequestMapping("/api/v1/agent-conversations/bizplay")
@RequiredArgsConstructor
public class RootAgentController {

    private final RootAgentService rootAgentService;

    @Operation(summary = "One chat turn. The root agent routes to the plan, settlement or booking "
            + "agent and answers as that agent; the reply carries the ROOT session id, so every "
            + "following turn comes back through here.")
    @PostMapping("/agents/root")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> rootChat(
            @RequestBody BizplayPlanAgentRequest request,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/root - corpNo={}, sessionId={}",
                request.getCorpNo(), request.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok(rootAgentService.chat(request, token)));
    }

    @Operation(summary = "Finish whatever is in progress — file the plan, submit the settlement or "
            + "confirm the booking. The caller does not say which; the root session remembers.")
    @PostMapping("/agents/root/{sessionId}/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> rootCreate(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/root/{}/create - corpNo={}", sessionId, corpNo);
        JsonNode approvalLines = body == null ? null : body.path("approvalLines");
        return ResponseEntity.ok(ApiResponse.ok(
                rootAgentService.create(sessionId, corpNo, token, approvalLines)));
    }
}
