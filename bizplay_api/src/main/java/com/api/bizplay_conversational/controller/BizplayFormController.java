package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayFormResponse;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.model.response.PurposeOption;
import com.api.bizplay_conversational.model.response.PurposeResolutionResult;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.api.bizplay_conversational.service.bizplayPlanAgentService.BizplayPlanAgentService;
import com.api.bizplay_conversational.service.formSkeletonService.FormSkeletonService;
import com.api.bizplay_conversational.service.purposeSegmentAgentService.PurposeSegmentAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.bizplay_conversational.model.request.AgentPromptRequest;
import com.api.bizplay_conversational.model.request.SettlementStarterRequest;
import com.api.bizplay_conversational.model.response.AgentPromptResponse;
import com.api.bizplay_conversational.model.response.SettlementStarterResponse;
import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;

import java.util.Arrays;
import java.util.List;

/**
 * Phase-1 surface of the BizPlay form-driven trip-plan flow: purpose catalog (①), purpose/segment
 * resolution (sub-agent), and the dynamic form skeleton (② → ③-shaped draft document). The end
 * user's BizPlay token is passed via the X-Bizplay-Token header (dev fallback configurable).
 */
@Slf4j
@Tag(name = "BizPlay Form Integration", description = "Dynamic, form-driven trip plan drafting against the BizPlay cloud API.")
@RestController
@RequestMapping("/api/v1/agent-conversations/bizplay")
@RequiredArgsConstructor
public class BizplayFormController {

    private final BizplayGatewayService bizplayGatewayService;
    private final PurposeSegmentAgentService purposeSegmentAgentService;
    private final FormSkeletonService formSkeletonService;
    private final BizplayPlanAgentService bizplayPlanAgentService;
    private final com.api.bizplay_conversational.service.bizplaySettlementAgentService.BizplaySettlementAgentService bizplaySettlementAgentService;
    private final AgentPromptService agentPromptService;
    private final com.api.bizplay_conversational.config.BizplayProperties bizplayProperties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Read currentCorpId from the (unverified) JWT payload — a lookup key, not authentication. */
    private Long corporationIdFromToken(String token) {
        String jwt = (token != null && !token.isBlank()) ? token : bizplayProperties.getDevToken();
        if (jwt == null || jwt.isBlank()) {
            return null;
        }
        try {
            String[] parts = jwt.trim().split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            long id = objectMapper.readTree(payload).path("currentCorpId").asLong(0);
            return id > 0 ? id : null;
        } catch (Exception e) {
            log.warn("Could not decode corporationId from token: {}", e.getMessage());
            return null;
        }
    }

    @Operation(summary = "Chat turn of the form-driven plan agent (purpose chips -> dynamic form -> field filling -> follow-ups)")
    @PostMapping("/agents/plan")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> planChat(
            @RequestBody BizplayPlanAgentRequest request,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/plan - corpNo={}, corpUserId={}, sessionId={}",
                request.getCorpNo(), request.getCorpUserId(), request.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok(bizplayPlanAgentService.chat(request, token)));
    }

    @Operation(summary = "Chat turn of the settlement (출장정산) agent: period question -> plan search "
            + "(④) -> plan import (⑤) -> evidence period/card-type questions -> receipt attach (⑥). "
            + "The session draft_json holds the sample-shaped settlement document.")
    @PostMapping("/agents/settlement")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> settlementChat(
            @RequestBody BizplayPlanAgentRequest request,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/settlement - corpNo={}, corpUserId={}, sessionId={}",
                request.getCorpNo(), request.getCorpUserId(), request.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.chat(request, token)));
    }

    @Operation(summary = "Create this plan: POST the session's draft_json to BizPlay (DRAFT_ONLY save). "
            + "Optional body {approvalLines:[{corporationUserId, approvalKindType?}]} carries the "
            + "\"Set approval order\" picks.")
    @PostMapping("/agents/plan/{sessionId}/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createPlan(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/plan/{}/create - corpNo={}", sessionId, corpNo);
        JsonNode approvalLines = body == null ? null : body.path("approvalLines");
        return ResponseEntity.ok(ApiResponse.ok(
                bizplayPlanAgentService.createPlan(sessionId, corpNo, token, approvalLines)));
    }

    @Operation(summary = "Submit this settlement: POST the session's draft_json to BizPlay's own "
            + "settlement endpoint (/bstr/report/draft — never the plan path). Optional body "
            + "{approvalLines:[{corporationUserId, approvalKindType?}]} carries the approver picks.")
    @PostMapping("/agents/settlement/{sessionId}/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createSettlement(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/settlement/{}/create - corpNo={}", sessionId, corpNo);
        JsonNode approvalLines = body == null ? null : body.path("approvalLines");
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.createSettlement(sessionId, corpNo, token, approvalLines)));
    }

    @Operation(summary = "Manual expense entry (⑧) — when the trip has no card receipt: register a "
            + "기타카드 expense + its required receipt image and map it into the settlement draft's "
            + "etcReceiptSaveRequests. multipart: 'expense' (JSON of the etc-card fields) + 'image' (file) "
            + "+ optional 'detail' (JSON of ReceiptEtcDto — vehicleType, depart/arrival, used dates, …).")
    @PostMapping(value = "/agents/settlement/{sessionId}/manual-expense", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> addSettlementManualExpense(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @org.springframework.web.bind.annotation.RequestPart("expense") String expenseJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "detail", required = false) String detailJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "image", required = false) org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense - corpNo={}", sessionId, corpNo);
        JsonNode fields = objectMapper.readTree(expenseJson);
        JsonNode detail = (detailJson == null || detailJson.isBlank()) ? null : objectMapper.readTree(detailJson);
        byte[] imageBytes = (image == null || image.isEmpty()) ? null : image.getBytes();
        String imageName = image == null ? null : image.getOriginalFilename();
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.addManualExpense(
                sessionId, corpNo, fields, detail, imageBytes, imageName, token)));
    }

    // --- settlement conversation starter (per-corp greeting + example prompts) --------------

    @Operation(summary = "Get this corp's settlement conversation starter — the chat greeting and "
            + "example prompts (custom override when set, else the built-in default).")
    @GetMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> getSettlementStarter(
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    @Operation(summary = "Set up this corp's settlement conversation starter: greeting and/or example "
            + "prompts. A provided value is saved; a blank/empty one resets that piece to the default. "
            + "Body {greeting, suggestions:[...]}.")
    @PutMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> setSettlementStarter(
            @RequestParam("corpNo") String corpNo,
            @RequestBody SettlementStarterRequest request) {
        log.info("PUT /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        applySettlementStarter(corpNo, request);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    /** POST alias of PUT — "create" reads naturally for first-time setup. */
    @Operation(summary = "Create this corp's settlement conversation starter (alias of PUT).")
    @PostMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> createSettlementStarter(
            @RequestParam("corpNo") String corpNo,
            @RequestBody SettlementStarterRequest request) {
        log.info("POST /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        applySettlementStarter(corpNo, request);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    @Operation(summary = "Reset this corp's settlement conversation starter (greeting + prompts) to defaults.")
    @DeleteMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> resetSettlementStarter(
            @RequestParam("corpNo") String corpNo) {
        log.info("DELETE /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE);
        agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    /** Save the provided pieces: greeting and suggestions each saved when given, reset when blank/empty. */
    private void applySettlementStarter(String corpNo, SettlementStarterRequest request) {
        if (request == null) {
            return;
        }
        if (request.getGreeting() != null) {
            if (request.getGreeting().isBlank()) {
                agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE);
            } else {
                AgentPromptRequest g = new AgentPromptRequest();
                g.setPrompt(request.getGreeting().trim());
                agentPromptService.put(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE, g);
            }
        }
        if (request.getSuggestions() != null) {
            List<String> cleaned = request.getSuggestions().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .toList();
            if (cleaned.isEmpty()) {
                agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS);
            } else {
                AgentPromptRequest s = new AgentPromptRequest();
                s.setPrompt(String.join("\n", cleaned));
                agentPromptService.put(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS, s);
            }
        }
    }

    /** The corp's effective settlement starter (custom-or-default greeting + prompts). */
    private SettlementStarterResponse settlementStarter(String corpNo) {
        AgentPromptResponse greeting =
                agentPromptService.get(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE);
        AgentPromptResponse suggestions =
                agentPromptService.get(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS);
        String raw = suggestions.getEffectivePrompt() == null ? "" : suggestions.getEffectivePrompt();
        List<String> lines = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return SettlementStarterResponse.builder()
                .greeting(greeting.getEffectivePrompt())
                .suggestions(lines)
                .greetingSource(greeting.getSource())
                .suggestionsSource(suggestions.getSource())
                .build();
    }

    @Operation(summary = "Manual (non-chat) plan create: values from the UI form are written into "
            + "the retrieved form's ③-shaped skeleton and POSTed to BizPlay.")
    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createManualPlan(
            @RequestBody com.api.bizplay_conversational.model.request.BizplayManualPlanRequest request,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/plans - corpUserId={}, purposeId={}, segmentId={}",
                request.getCorpUserId(), request.getPurposeId(), request.getSegmentId());
        return ResponseEntity.ok(ApiResponse.ok(bizplayPlanAgentService.createManualPlan(request, token)));
    }

    @Operation(summary = "Staff roster of a corporation (for the Set-approval-order picker). "
            + "corporationId falls back to the token's currentCorpId.")
    @GetMapping("/corporation-users")
    public ResponseEntity<ApiResponse<JsonNode>> corporationUsers(
            @RequestParam(value = "corporationId", required = false) Long corporationId,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        Long corpId = corporationId != null ? corporationId : corporationIdFromToken(token);
        if (corpId == null) {
            throw new IllegalArgumentException("corporationId is required (none in token).");
        }
        log.info("GET /bizplay/corporation-users - corporationId={}", corpId);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getCorporationUsers(corpId, token)));
    }

    @Operation(summary = "List selectable Travel-Purpose × Trip-Type options for a corporation user")
    @GetMapping("/purposes")
    public ResponseEntity<ApiResponse<List<PurposeOption>>> purposes(
            @RequestParam("corpUserId") String corpUserId,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/purposes - corpUserId={}", corpUserId);
        JsonNode catalog = bizplayGatewayService.getPurposeCatalog(corpUserId, token);
        return ResponseEntity.ok(ApiResponse.ok(purposeSegmentAgentService.flattenCatalog(catalog)));
    }

    @Operation(summary = "Resolve a user's message to a purpose/segment (or candidate chips)")
    @PostMapping("/resolve-purpose")
    public ResponseEntity<ApiResponse<PurposeResolutionResult>> resolvePurpose(
            @RequestBody ResolvePurposeRequest request,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/resolve-purpose - corpUserId={}", request.getCorpUserId());
        JsonNode catalog = bizplayGatewayService.getPurposeCatalog(request.getCorpUserId(), token);
        List<PurposeOption> options = purposeSegmentAgentService.flattenCatalog(catalog);
        return ResponseEntity.ok(ApiResponse.ok(
                purposeSegmentAgentService.resolve(request.getMessage(), options)));
    }

    @Operation(summary = "Fetch the dynamic form for a purpose/segment as a save-ready draft skeleton")
    @GetMapping("/form")
    public ResponseEntity<ApiResponse<BizplayFormResponse>> form(
            @RequestParam("purposeId") long purposeId,
            @RequestParam(value = "segmentId", required = false) Long segmentId,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/form - purposeId={}, segmentId={}", purposeId, segmentId);
        JsonNode papers = bizplayGatewayService.getPapers(purposeId, segmentId, token);
        return ResponseEntity.ok(ApiResponse.ok(
                formSkeletonService.buildPlanSkeleton(papers, purposeId, segmentId)));
    }

    @Getter
    @Setter
    public static class ResolvePurposeRequest {
        private String corpUserId;
        private String message;
    }
}
