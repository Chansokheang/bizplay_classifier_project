package com.api.bizplay_conversational.service.agentPromptService;

import com.api.bizplay_conversational.model.request.AgentPromptRequest;
import com.api.bizplay_conversational.model.response.AgentModuleResponse;
import com.api.bizplay_conversational.model.response.AgentPromptResponse;

import java.util.List;

/**
 * Runtime prompt customization for the conversational sub-agents. Each agent calls
 * {@link #resolve(String, String)} with its compiled-in default — a stored, enabled custom prompt
 * overrides it; anything else (no row, disabled, DB unavailable) falls back to the default, so a
 * bad or missing customization can never take an agent down.
 */
public interface AgentPromptService {

    /** Name of the pseudo-agent holding the chat opener text shown by the web UI. */
    String STARTER_MESSAGE = "starter-message";

    /** Pseudo-agent holding the chat example prompts (one suggestion per line). */
    String STARTER_SUGGESTIONS = "starter-suggestions";

    /** Settlement (출장정산) agent's own chat opener — independent from the plan agent's. */
    String SETTLEMENT_STARTER_MESSAGE = "settlement-starter-message";

    /** Settlement agent's own example prompts (one suggestion per line). */
    String SETTLEMENT_STARTER_SUGGESTIONS = "settlement-starter-suggestions";

    /**
     * The prompt the agent should use right now: the current turn's corp (from
     * {@link AgentTenantContext}) may have a custom override; otherwise the given default.
     */
    String resolve(String name, String builtInDefault);

    /** Register an agent's default so it appears in {@link #list(String)} before its first call. */
    void registerDefault(String name, String defaultPrompt);

    List<AgentPromptResponse> list(String corpNo);

    AgentPromptResponse get(String corpNo, String name);

    /** Create or update {@code corpNo}'s custom prompt for {@code name}. */
    AgentPromptResponse put(String corpNo, String name, AgentPromptRequest request);

    /** Delete {@code corpNo}'s custom prompt — the agent returns to its built-in default. */
    void reset(String corpNo, String name);

    // --- sub-agent module on/off (per corp) ------------------------------------------------

    /** Whether the module runs for the CURRENT turn's corp ({@link AgentTenantContext}); fixed modules are always on. */
    boolean isModuleEnabled(String name);

    List<AgentModuleResponse> listModules(String corpNo);

    AgentModuleResponse setModule(String corpNo, String name, boolean enabled);
}
