package com.api.bizplay_conversational.service.fieldMapperAgentService;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Field Mapper sub-agent: given the RETRIEVED form's field list (labels/types/options/required —
 * whatever ② returned, never a hardcoded schema) and the user's text, produce normalized values
 * keyed by field key. Data-only: the deterministic FormValueWriterService encodes the results into
 * the draft document.
 */
public interface FieldMapperAgentService {

    /**
     * @param message user text (possibly several staged messages concatenated)
     * @param fields  the zone's field spec array
     * @return object node {"<fieldKey>": <normalized value>} — empty when nothing was extracted
     */
    JsonNode mapFields(String message, JsonNode fields);

    /**
     * The same, with the tail of the conversation for CONTEXT ONLY — enough to resolve an
     * elliptical turn ("make it the day after", "the other one") without re-reading older values.
     * A field the latest message does not state must still come back absent: an earlier value is
     * usually the very thing being corrected.
     *
     * @param recentTurns last few turns, oldest first, formatted "user: …" / "assistant: …";
     *                    null or empty behaves exactly like the 2-arg form.
     */
    default JsonNode mapFields(String message, JsonNode fields, java.util.List<String> recentTurns) {
        return mapFields(message, fields);
    }

    /**
     * Focused re-read for ONE field, used when the broad mapping missed something the user
     * clearly said ("...trip to Busan..." with no destination extracted). Asking about a single
     * named field is far more reliable than mapping the whole form at once.
     *
     * @return the extracted value, or null when the message genuinely does not contain it.
     */
    String extractField(String message, JsonNode field);
}
