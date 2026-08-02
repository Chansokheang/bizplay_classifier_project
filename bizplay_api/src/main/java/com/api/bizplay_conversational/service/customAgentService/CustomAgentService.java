package com.api.bizplay_conversational.service.customAgentService;

import com.api.bizplay_conversational.model.request.CustomAgentRequest;
import com.api.bizplay_conversational.model.response.CustomAgentResponse;

import java.util.List;
import java.util.Map;

/**
 * User-defined sub-agents (Phase 1): runtime-created agents with a system prompt, an optional
 * model override, and a built-in READ-ONLY tool allowlist. The orchestrator offers each chat
 * turn to {@link #tryHandle}; a routed agent answers via a bounded tool-calling loop.
 */
public interface CustomAgentService {

    List<CustomAgentResponse> list(String corpNo);

    CustomAgentResponse put(String corpNo, String name, CustomAgentRequest request);

    void delete(String corpNo, String name);

    /** The built-in tool allowlist: key -> human description (shown in the builder UI). */
    Map<String, String> toolCatalog();

    /** Run one agent directly with a test message (the builder's Test box). */
    CustomAgentResponse test(String corpNo, String name, String message);

    /**
     * Offer a chat turn to the corp's custom agents: route (LLM pick by "when to use"), and if
     * one matches, run it. Returns null when no agent claims the message — the caller continues
     * its normal flow.
     */
    RoutedReply tryHandle(String corpNo, String message);

    record RoutedReply(String agentName, String reply, List<String> toolsUsed) {
    }
}
