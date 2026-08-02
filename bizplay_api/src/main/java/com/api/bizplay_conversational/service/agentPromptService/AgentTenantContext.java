package com.api.bizplay_conversational.service.agentPromptService;

/**
 * Per-request tenant holder for prompt resolution. The orchestrator sets the turn's corpNo before
 * running sub-agents (same thread) and clears it in a finally-block; {@code resolve()} reads it so
 * the individual agents never need a corpNo in their signatures. No tenant set = defaults apply.
 */
public final class AgentTenantContext {

    private static final ThreadLocal<String> CORP_NO = new ThreadLocal<>();

    private AgentTenantContext() {
    }

    public static void set(String corpNo) {
        if (corpNo == null || corpNo.isBlank()) {
            CORP_NO.remove();
        } else {
            CORP_NO.set(corpNo.trim());
        }
    }

    public static String get() {
        return CORP_NO.get();
    }

    public static void clear() {
        CORP_NO.remove();
    }
}
