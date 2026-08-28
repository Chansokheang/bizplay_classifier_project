package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.service.bookingDemoAgentService.BookingDemoAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEMO ONLY - the proposed booking agent, backed by dummy inventory.
 *
 * <p>Deliberately its own controller and its own service package so the whole feature can be
 * deleted in one move when a real booking API or booking sub-agent replaces it. Nothing else in
 * the codebase references it, and it calls nothing outside itself - no BizPlay endpoint, no LLM,
 * no database.
 *
 * <p>The request and response types are the SAME ones the plan and settlement agents use, which
 * is the point being demonstrated: booking needs no new contract and no new UI.
 */
@Slf4j
@Tag(name = "Booking Agent (DEMO)",
        description = "Feasibility demo of a booking agent over dummy inventory. Not wired to any provider.")
@RestController
@RequestMapping("/api/v1/agent-conversations/bizplay")
@RequiredArgsConstructor
public class BookingDemoController {

    private final BookingDemoAgentService bookingDemoAgentService;

    @Operation(summary = "DEMO chat turn of the booking agent: work out what is being booked, offer "
            + "dummy inventory as chips, then confirm. Same request/response shape as /agents/plan.")
    @PostMapping("/agents/booking")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> bookingChat(
            @RequestBody BizplayPlanAgentRequest request) {
        log.info("POST /bizplay/agents/booking [DEMO] - corpUserId={}, sessionId={}",
                request.getCorpUserId(), request.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok(bookingDemoAgentService.chat(request)));
    }

    @Operation(summary = "DEMO confirm: a separate call on purpose, so a chat turn can never book "
            + "on its own. Returns the booking reference and the receiptIds it would become.")
    @PostMapping("/agents/booking/{sessionId}/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> confirmBooking(
            @PathVariable("sessionId") String sessionId) {
        log.info("POST /bizplay/agents/booking/{}/create [DEMO]", sessionId);
        return ResponseEntity.ok(ApiResponse.ok(bookingDemoAgentService.confirm(sessionId)));
    }
}
