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
}
