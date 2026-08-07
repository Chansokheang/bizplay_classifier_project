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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
