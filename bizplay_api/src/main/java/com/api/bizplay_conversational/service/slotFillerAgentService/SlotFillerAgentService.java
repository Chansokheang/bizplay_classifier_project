package com.api.bizplay_conversational.service.slotFillerAgentService;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Slot-Filler sub-agent — works under the settlement agent with ONE focused job: read a user's
 * free-text message and return the request parameters it clearly states, so a single answer can
 * fill several slots at once (period + card type + a plan hint). It does not ask, route, decide, or
 * call any endpoint — the settlement agent orchestrates; the follow-up agent phrases questions; the
 * plan-picker matches a plan; this only extracts.
 *
 * <p>It augments, never replaces, the settlement agent's deterministic parsing: on any model
 * failure it returns an empty object so the caller falls back to its own parsers and to asking.
 */
public interface SlotFillerAgentService {

    /**
     * @param message     the user's free-text turn
     * @param wantedSlots slot name -> one-line meaning; the ONLY fields to look for
     * @param korean      conversation language (affects how relative dates / card words are read)
     * @return a JSON object carrying ONLY the wanted slots the message states, omitting the rest;
     *         an empty object when nothing is found or the model is unavailable. Conventions:
     *         dates as ISO {@code yyyy-MM-dd}; {@code cardTypes} as an array of CORP|PERSONAL|MY_DATA.
     */
    JsonNode extract(String message, Map<String, String> wantedSlots, boolean korean);

    /**
     * The same, with the tail of the conversation for CONTEXT ONLY. Values are still taken from
     * {@code message} alone - the history is there so an elliptical turn can be understood ("the
     * second one", "make it the day after"), never so that an older value gets re-extracted.
     *
     * @param recentTurns the last few turns, oldest first, already formatted as "user: ..." /
     *                    "assistant: ..."; null or empty behaves exactly like the 3-arg form.
     */
    default JsonNode extract(String message, Map<String, String> wantedSlots, boolean korean,
                             java.util.List<String> recentTurns) {
        return extract(message, wantedSlots, korean);
    }
}
