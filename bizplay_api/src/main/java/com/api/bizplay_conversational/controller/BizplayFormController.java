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
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final com.api.bizplay_conversational.service.planEnrichmentService.PlanEnrichmentService planEnrichmentService;
    private final com.api.bizplay_conversational.service.destinationResolverAgentService.DestinationResolverAgentService destinationResolverAgentService;
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

    @Operation(summary = "⑨-b One settlement document by approvalId (GET /api/v2/approval/bstr/{id}). "
            + "paper.paperKind.paperKindType == EXPENSE_REPORT confirms the id really is a settlement.")
    @GetMapping("/settlements/{approvalId}")
    public ResponseEntity<ApiResponse<JsonNode>> settlementDetail(
            @org.springframework.web.bind.annotation.PathVariable("approvalId") long approvalId,
            @RequestParam("corpNo") String corpNo,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/settlements/{} - corpNo={}", approvalId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getPlanDetail(approvalId, token)));
    }

    @Operation(summary = "Who the caller is, per their BizPlay token: name, corporationUserId, "
            + "department, connected corporations. With no X-Bizplay-Token header this describes "
            + "the dev fallback token — which is exactly what the UI shows as the default user.")
    @GetMapping("/whoami")
    public ResponseEntity<ApiResponse<com.fasterxml.jackson.databind.JsonNode>> whoami(
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getUserProfile(token)));
    }

    @Operation(summary = "Destination suggestion chips for one trip form — the regions the "
            + "paper's OWN flags allow (급지 policy list / 시도 list), from the provider's region "
            + "APIs. Empty regions with source=any means anything goes and the UI may show its "
            + "generic suggestions.")
    @GetMapping("/agents/plan/destination-options")
    public ResponseEntity<ApiResponse<com.fasterxml.jackson.databind.JsonNode>> destinationOptions(
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "segment", required = false) String segment,
            @RequestParam(value = "purposeId", required = false) Long purposeId,
            @RequestParam(value = "segmentId", required = false) Long segmentId,
            @RequestParam(value = "citiesOf", required = false) String citiesOf,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            if (citiesOf != null && !citiesOf.isBlank()) {
                // Second step of the country→city cascade on non-policy overseas forms.
                return ResponseEntity.ok(ApiResponse.ok(
                        destinationResolverAgentService.citiesOfCountry(citiesOf, token)));
            }
            return ResponseEntity.ok(ApiResponse.ok(destinationResolverAgentService.destinationOptions(
                    purpose, segment, purposeId, segmentId, token)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "Semantic destination pick: which listed option does the typed message "
            + "MEAN? Body {message, options:[labels…]} → {index: 0-based or null}. LLM-judged — "
            + "handles other languages, typos and descriptions ('capital of Japan').")
    @PostMapping("/agents/plan/destination-pick")
    public ResponseEntity<ApiResponse<com.fasterxml.jackson.databind.JsonNode>> destinationPick(
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            java.util.List<String> options = new java.util.ArrayList<>();
            for (JsonNode o : body.path("options")) {
                options.add(o.asText(""));
            }
            String message = body.path("message").asText("");
            boolean ko = message.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
            Integer idx = destinationResolverAgentService.pickDestination(options, message,
                    body.path("context").asText(""), ko);
            com.fasterxml.jackson.databind.node.ObjectNode out =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            if (idx == null) {
                out.putNull("index");
            } else {
                out.put("index", idx);
            }
            return ResponseEntity.ok(ApiResponse.ok(out));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "⑨ Settlement (출장정산서) documents saved in BizPlay for a period — the "
            + "source of truth for the Expense Report table.")
    @GetMapping("/settlements")
    public ResponseEntity<ApiResponse<JsonNode>> settlements(
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String from = (startDate == null || startDate.isBlank()) ? today.minusMonths(1).toString() : startDate;
        String to = (endDate == null || endDate.isBlank()) ? today.toString() : endDate;
        log.info("GET /bizplay/settlements - corpNo={}, {}~{}", corpNo, from, to);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getSettlementList(from, to, token)));
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

    @Operation(summary = "LLM intent judge for the approval-line step: what does the user's "
            + "message MEAN — pick a person, assign a role, done picking, save now, not yet, "
            + "remove someone, or something else. No word lists.")
    @PostMapping("/agents/plan/approval-intent")
    public ResponseEntity<ApiResponse<JsonNode>> approvalIntent(
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            return ResponseEntity.ok(ApiResponse.ok(bizplayPlanAgentService.approvalIntent(corpNo, body)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "The corporation's registered travel destinations (출장지) — what a "
            + "route leg's departure/arrival ids, addresses and coordinates come from. Offer them "
            + "as a picker; typing the route in words works too.")
    @GetMapping("/agents/plan/route-options")
    public ResponseEntity<ApiResponse<JsonNode>> planRouteOptions(
            @RequestParam("corpNo") String corpNo,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String bizplayToken) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            return ResponseEntity.ok(ApiResponse.ok(planEnrichmentService.routeOptions(bizplayToken)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "Record a client-handled turn into the plan session transcript — "
            + "no pipeline runs; keeps the conversation history complete for context.")
    @PostMapping("/agents/plan/{sessionId}/note")
    public ResponseEntity<ApiResponse<String>> notePlanTurn(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body) {
        bizplayPlanAgentService.noteTurn(sessionId, corpNo,
                body.path("user").asText(null), body.path("assistant").asText(null));
        return ResponseEntity.ok(ApiResponse.ok("noted"));
    }

    @Operation(summary = "Correct ONE field of a plan draft in place - the same change a user "
            + "would otherwise phrase in chat. Body {key, value}; key is a form field key "
            + "(\"basic:BASIC_TITLE\", \"item:18403\") or a slot name (destination, "
            + "destinationDetail, startDate, endDate, transportType). Returns the updated draft.")
    @PatchMapping("/agents/plan/{sessionId}/field")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> editPlanField(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String bizplayToken) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            return ResponseEntity.ok(ApiResponse.ok(bizplayPlanAgentService.editField(
                    sessionId, corpNo, body.path("key").asText(null),
                    body.path("value").asText(""), bizplayToken)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
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

    @Operation(summary = "List this corp's saved settlements (summary rows) for the settlements table.")
    @GetMapping("/agents/settlement/saved")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> listSavedSettlements(
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /bizplay/agents/settlement/saved - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.listSettlements(corpNo)));
    }

    @Operation(summary = "Finalize the settlement in OUR DB only — marks the session APPROVED and "
            + "persists its draft_json, independent of the BizPlay report/draft POST.")
    @PostMapping("/agents/settlement/{sessionId}/save")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> saveSettlementToDb(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo) {
        log.info("POST /bizplay/agents/settlement/{}/save - corpNo={}", sessionId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.saveSettlement(corpNo, sessionId)));
    }

    @Operation(summary = "Load a settlement session's current state (draft_json + status). Restores the "
            + "registered-expenses table after the chat is closed and reopened.")
    @GetMapping("/agents/settlement/{sessionId}")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> getSettlementSession(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /bizplay/agents/settlement/{} - corpNo={}", sessionId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.getSession(corpNo, sessionId)));
    }

    @Operation(summary = "Manual expense ⑧ STEP 1 — register the receipt with the base fields only "
            + "(POST /receipt/etc-card). Body {approvalDate, mestName, approvalAmount, …}. Returns the "
            + "created receipt (stashed) + the type-specific detail fields to collect in step 2.")
    @PostMapping("/agents/settlement/{sessionId}/manual-expense/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createSettlementManualReceipt(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode expense,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense/create - corpNo={}", sessionId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.createManualReceipt(sessionId, corpNo, expense, token)));
    }

    @Operation(summary = "Manual expense ⑧ COMPLETE — register the whole receipt in one etc-card POST "
            + "(base + TranKind + detail + image). multipart: 'expense' (JSON base) + optional 'detail' "
            + "(JSON ReceiptEtcDto) + optional 'image' (file).")
    @PostMapping(value = "/agents/settlement/{sessionId}/manual-expense/complete", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> completeSettlementManualFull(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @org.springframework.web.bind.annotation.RequestPart("expense") String expenseJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "detail", required = false) String detailJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "image", required = false) org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense/complete - corpNo={}", sessionId, corpNo);
        JsonNode fields = objectMapper.readTree(expenseJson);
        JsonNode detail = (detailJson == null || detailJson.isBlank()) ? null : objectMapper.readTree(detailJson);
        byte[] imageBytes = (image == null || image.isEmpty()) ? null : image.getBytes();
        String imageName = image == null ? null : image.getOriginalFilename();
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.addManualExpense(
                sessionId, corpNo, fields, detail, imageBytes, imageName, token)));
    }

    @Operation(summary = "Manual expense ⑧ STEP 2 — complete the receipt from step 1: PATCH "
            + "/receipt-etc/{id} with the additional detail + optional image, and map it into the draft. "
            + "multipart: optional 'detail' (JSON ReceiptEtcDto) + optional 'image' (file).")
    @PostMapping(value = "/agents/settlement/{sessionId}/manual-expense/detail", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> completeSettlementManualReceipt(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @org.springframework.web.bind.annotation.RequestPart(value = "detail", required = false) String detailJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "image", required = false) org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense/detail - corpNo={}", sessionId, corpNo);
        JsonNode detail = (detailJson == null || detailJson.isBlank()) ? null : objectMapper.readTree(detailJson);
        byte[] imageBytes = (image == null || image.isEmpty()) ? null : image.getBytes();
        String imageName = image == null ? null : image.getOriginalFilename();
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.completeManualReceipt(
                sessionId, corpNo, detail, imageBytes, imageName, token)));
    }

    @Operation(summary = "Transport terminals/stations for the manual-expense depart/arrival dropdowns. "
            + "Optional vehicleType filter (AIR = airports, KTX = rail stations, BUS = bus terminals). "
            + "Returns [{id, name}].")
    @GetMapping("/agents/settlement/terminals")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> settlementTerminals(
            @RequestParam(value = "vehicleType", required = false) String vehicleType,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/terminals - vehicleType={}", vehicleType);
        JsonNode all = bizplayGatewayService.getEtcCardTerminals(token);
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        if (all != null && all.isArray()) {
            for (JsonNode t : all) {
                if (vehicleType != null && !vehicleType.isBlank()
                        && !vehicleType.equalsIgnoreCase(t.path("vehicleType").asText())) {
                    continue;
                }
                out.add(java.util.Map.of("id", t.path("id").asLong(), "name", t.path("name").asText("")));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    // --- personal-card general-expense browser (search receipts by date + status) -----------

    @Operation(summary = "Browse personal-card general expenses. status=NOT_DRAFTED (issued, default) or "
            + "NOT_ISSUED (incomplete). Paginated (default size 10). Returns the receipt-row array.")
    @GetMapping("/agents/settlement/receipts")
    public ResponseEntity<ApiResponse<JsonNode>> browseReceipts(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "status", required = false, defaultValue = "NOT_DRAFTED") String status,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/receipts - {}~{} status={} page={} size={}",
                startDate, endDate, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getGeneralExpenses(
                startDate, endDate, List.of(status), page, size, token)));
    }

    @Operation(summary = "One receipt's detail. NOT_ISSUED → GET /receipt/{id}; ISSUED → issued/bulk/{id}.")
    @GetMapping("/agents/settlement/receipts/{receiptId}")
    public ResponseEntity<ApiResponse<JsonNode>> receiptDetail(
            @org.springframework.web.bind.annotation.PathVariable("receiptId") long receiptId,
            @RequestParam(value = "status", required = false, defaultValue = "ISSUED") String status,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/receipts/{} - status={}", receiptId, status);
        JsonNode detail = "NOT_ISSUED".equalsIgnoreCase(status)
                ? bizplayGatewayService.getReceiptById(receiptId, token)
                : bizplayGatewayService.getIssuedReceiptsBulk(List.of(receiptId), token);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @Operation(summary = "Upload a file (filebox, pdf2Img) and attach it to a receipt "
            + "(PATCH /receipt/image/{id}). multipart: 'image' (file).")
    @PostMapping(value = "/agents/settlement/receipts/{receiptId}/image", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<JsonNode>> attachReceiptImage(
            @org.springframework.web.bind.annotation.PathVariable("receiptId") long receiptId,
            @org.springframework.web.bind.annotation.RequestPart("image") org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/receipts/{}/image", receiptId);
        long fileId = bizplayGatewayService.uploadReceiptFile(image.getBytes(), image.getOriginalFilename(), token);
        bizplayGatewayService.attachReceiptImages(receiptId, List.of(fileId), token);
        com.fasterxml.jackson.databind.node.ObjectNode result = objectMapper.createObjectNode();
        result.put("fileId", fileId);
        result.put("receiptId", receiptId);
        return ResponseEntity.ok(ApiResponse.ok(result));
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

    @Operation(summary = "Trip-plan requests by approval state. status=DRAFTED (default) lists the "
            + "plans still waiting for an approver - those CANNOT be settled; status=APPROVED lists "
            + "the settleable ones. Defaults to the last month. Rows are deduped by approvalId.")
    @GetMapping("/plans/by-status")
    public ResponseEntity<ApiResponse<JsonNode>> plansByStatus(
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "travelerId", required = false) Long travelerId,
            @RequestParam(value = "status", required = false, defaultValue = "DRAFTED") String status,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String from = (startDate == null || startDate.isBlank()) ? today.minusMonths(1).toString() : startDate;
        String to = (endDate == null || endDate.isBlank()) ? today.toString() : endDate;
        long who = travelerId != null ? travelerId
                : Long.parseLong(bizplayProperties.getDefaultCorpUserId().trim());
        log.info("GET /bizplay/plans/by-status - corpNo={}, travelerId={}, status={}, {}~{}",
                corpNo, who, status, from, to);
        // DRAFTED rows exist ONLY on the unscoped path; the scoped one answers with APPROVED only.
        JsonNode raw = "APPROVED".equalsIgnoreCase(status)
                ? bizplayGatewayService.getPlanList(who, from, to, token)
                : bizplayGatewayService.getPendingPlanList(who, from, to, token);
        com.fasterxml.jackson.databind.node.ArrayNode out = objectMapper.createArrayNode();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode p : raw) {
                if (!status.equalsIgnoreCase(p.path("approvalStatusType").asText(""))) {
                    continue;
                }
                // The unscoped path ignores the period and repeats a plan per approval line.
                String start = p.path("bstrStartDate").asText("");
                String end = p.path("bstrEndDate").asText("");
                start = start.length() >= 10 ? start.substring(0, 10) : start;
                end = end.length() >= 10 ? end.substring(0, 10) : end;
                if (start.isEmpty() || end.isEmpty() || start.compareTo(to) > 0 || end.compareTo(from) < 0) {
                    continue;
                }
                if (seen.add(p.path("approvalId").asLong())) {
                    out.add(p);
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
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
