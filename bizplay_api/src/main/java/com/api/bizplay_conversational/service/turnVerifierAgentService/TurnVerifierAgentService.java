package com.api.bizplay_conversational.service.turnVerifierAgentService;

/**
 * Turn Verifier sub-agent — the alignment critic. After a turn that CHANGED the draft, it is
 * shown the user's message, a compact before/after of the draft state, and the agent's reply,
 * and answers one question: did the action + reply match what the user asked?
 *
 * <p>GATING (current): {@link #verify} is called synchronously before the plan agent answers a
 * turn that changed the draft. On {@code aligned=false} the agent rewinds the draft to exactly
 * where the turn began and runs it once more with the verifier's note in front of the message —
 * then that second answer stands, whatever the verdict. One correction, never a loop; a verifier
 * that cannot answer reports aligned, so it can never hold up a reply. {@link #verifyShadow}
 * remains for the retry itself, which is judged for the record only.
 *
 * <p>Cost: one extra LLM call on turns that changed something (measured ~5% of them then run a
 * second time). Turns that changed nothing — questions, chit-chat — are never verified.
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

    /**
     * The same judgement, answered SYNCHRONOUSLY so the caller can act on it — the gating path.
     * Never throws: on any failure it reports aligned, because a verifier that cannot answer
     * must not hold up a reply.
     */
    Verdict verify(String userMessage, String before, String after, String reply, boolean korean);

    /** {@code aligned=false} carries the one thing the turn missed, in the verifier's words. */
    record Verdict(boolean aligned, String issue) { }
}
