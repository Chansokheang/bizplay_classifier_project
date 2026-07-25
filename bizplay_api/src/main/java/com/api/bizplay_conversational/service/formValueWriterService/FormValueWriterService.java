package com.api.bizplay_conversational.service.formValueWriterService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Deterministic field writers: encode NORMALIZED values (from the Field Mapper agent) into the plan
 * draft {@code document}, which keeps EXACTLY the structure of the BizPlay plan-draft request body —
 * values are only updated in place, never restructured. Facts that have no slot in that body yet
 * (traveler NAMES awaiting corporationUserId resolution, the destination that rides the BSTR_PERIOD
 * selections) live in the agent {@code state} node, never inside the document. LLMs never write
 * here — this layer owns the save-body encodings (BSTR_PERIOD dates riding
 * selectionName/selectionErpCode, option picks as selections, plain text in value) with a
 * shape-based fallback for item types it has never seen.
 */
public interface FormValueWriterService {

    /**
     * Apply mapped values ({fieldKey: normalizedValue}) onto the document (③ structure, in place).
     *
     * @return human-readable descriptions of the writes that were applied (for the reply).
     */
    List<String> apply(ObjectNode document, JsonNode fields, ObjectNode state, JsonNode mappedValues);

    /** Labels of required fields that are still empty (drives follow-up questions + status). */
    List<String> missingRequired(JsonNode document, JsonNode fields, JsonNode state);
}
