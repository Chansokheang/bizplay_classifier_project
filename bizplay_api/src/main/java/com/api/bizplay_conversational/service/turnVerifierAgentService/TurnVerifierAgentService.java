package com.api.bizplay_conversational.service.turnVerifierAgentService;

/**
 * Turn Verifier sub-agent — the alignment critic. After a turn that CHANGED the draft, it is
 * shown the user's message, a compact before/after of the draft state, and the agent's reply,
 * and answers one question: did the action + reply match what the user asked?
 *
 * <p>SHADOW MODE (current): called asynchronously after the response is already on its way, so
 * it costs zero latency; verdicts go to the log as {@code [VERIFY]} lines only. The misalignment
 * rate measured there decides whether this ever graduates to a gating retry — an unmeasured
 * verifier that blocks good replies would make the agent feel dumber, not smarter.
 *
 * <p>Scope note: it catches MISALIGNMENT (a requested change the draft does not reflect, an
 * ignored request, an unrequested change), not wrong facts — data validity stays with the
 * deterministic layer (region master, enums, invariants).
 */
public interface TurnVerifierAgentService {

    /**
     * Judge one turn and LOG the verdict; never throws, never blocks the reply.
     *
     * @param userMessage the user's words (client context preamble stripped)
     * @param before      compact draft/slot summary at turn start
     * @param after       the same summary at turn end
     * @param reply       what the agent answered
     * @param korean      conversation language
     */
    void verifyShadow(String userMessage, String before, String after, String reply, boolean korean);
}
