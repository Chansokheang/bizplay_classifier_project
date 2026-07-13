package com.api.bizplay_conversational.service.llmSettingsService;

import com.api.bizplay_conversational.model.response.LlmSettingsResponse;

/**
 * Runtime selection of which LLM the conversational sub-agents use. Agents call {@link #resolve}
 * with their own configured default; when an override is set (via {@link #setActiveModel}) it wins,
 * otherwise the agent's default is used. The selection is persisted so it survives restarts.
 */
public interface LlmSettingsService {

    /**
     * Resolve the model name an agent should use: the active override when one is set, else the
     * supplied per-agent default. Cheap (reads an in-memory cache) — safe to call on every request.
     */
    String resolve(String agentDefaultModel);

    /** Current selection + the list of selectable (registered) model names. */
    LlmSettingsResponse getSettings();

    /**
     * Set the active model for all conversational sub-agents. Pass a registered model name, or null/
     * blank to clear the override (agents revert to their own defaults). Throws
     * IllegalArgumentException if the name is not a registered model.
     */
    LlmSettingsResponse setActiveModel(String model);
}
