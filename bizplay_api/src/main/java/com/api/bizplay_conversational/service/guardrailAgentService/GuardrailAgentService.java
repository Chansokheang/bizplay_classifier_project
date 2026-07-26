package com.api.bizplay_conversational.service.guardrailAgentService;

/**
 * Entry guardrail for every conversational-agent turn — runs BEFORE any LLM call or session
 * write. Deterministic (regex-based, zero latency, model-independent) screening for:
 *
 * <ul>
 *   <li>{@code DB_MUTATION} — natural language (or raw SQL) asking the NL→SQL database-lookup
 *       agent to INSERT / UPDATE / DELETE / DROP / TRUNCATE / ALTER. The SQL layer already
 *       enforces SELECT-only as defense-in-depth; this refuses the request up front.</li>
 *   <li>{@code INJECTION} — prompt-injection phrasing ("ignore your instructions",
 *       "reveal the system prompt", Korean equivalents).</li>
 *   <li>{@code INPUT_TOO_LONG} — oversized messages that would blow up prompt budgets.</li>
 * </ul>
 */
public interface GuardrailAgentService {

    /** Screen one user message. {@code allowed=true} means the pipeline may proceed. */
    GuardrailResult check(String message);

    record GuardrailResult(boolean allowed, String category, String reply) {
        public static GuardrailResult ok() {
            return new GuardrailResult(true, null, null);
        }

        public static GuardrailResult blocked(String category, String reply) {
            return new GuardrailResult(false, category, reply);
        }
    }
}
