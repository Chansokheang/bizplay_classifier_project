package com.api.bizplay_conversational.service.requestBodyBuilderService;

import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.DraftEditPlan;
import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.model.response.TextAnalysisResult;

import java.util.List;

/**
 * "Structured API RequestBody" component from the flow: takes the data-only outputs of the
 * fan-out agents and constructs / accumulates the session draft_json. The agents themselves
 * never touch draft_json — all assembly happens here.
 */
public interface RequestBodyBuilderService {

    /** Merge a matched staff member (from the Staff Lookup Agent) into the session draft_json. */
    void mergeStaff(ConversationalAgentSession session, StaffLookupResult staffResult);

    /** Merge extracted trip information authoritatively (existing values may be overwritten). */
    default void mergeTextAnalysis(ConversationalAgentSession session, TextAnalysisResult analysis) {
        mergeTextAnalysis(session, analysis, true);
    }

    /**
     * Merge extracted trip information (from the Text Analysis or PDF Agent) into draft_json.
     *
     * @param authoritative when {@code true} (e.g. the user's typed message), present values overwrite
     *                      existing ones; when {@code false} (e.g. a supplementary PDF), only blanks are
     *                      filled so a higher-priority source is never clobbered.
     */
    void mergeTextAnalysis(ConversationalAgentSession session, TextAnalysisResult analysis, boolean authoritative);

    /** Merge resolved staff rows (from the Spreadsheet Agent) into the session draft_json. */
    void mergeSpreadsheet(ConversationalAgentSession session, SpreadsheetAnalysisResult spreadsheet);

    /**
     * Record an uploaded file in the draft's attachment list (Type=File, FileID=fileId) so it is
     * persisted to conversational_attachment when the trip plan is created. Deduplicated by fileId.
     */
    void mergeAttachment(ConversationalAgentSession session, String fileId);

    /**
     * Apply a Draft Update Agent edit plan (set/clear/add/remove on trip + traveler fields) against
     * a fixed op/field allowlist. Returns a human-readable description of each applied edit (empty
     * when nothing valid was applied).
     */
    List<String> applyEdits(ConversationalAgentSession session, DraftEditPlan plan);

    /**
     * Since one session is one business trip, propagate the trip's common route fields
     * (origin / destination / return point / transportation) onto every traveler that is still
     * missing them — e.g. staff added later from a spreadsheet inherit the shared route. Only blanks
     * are filled; existing per-traveler values are never overwritten, and ambiguous fields (where
     * travelers already disagree) are left alone.
     */
    void inheritCommonRoute(ConversationalAgentSession session);

    /** Deserialize the session's current draft_json into the typed draft (never null). */
    TripPlanDraft snapshot(ConversationalAgentSession session);

    /** Write the computed missing-field descriptors into the draft's {@code missingFields} list. */
    void stampMissingFields(ConversationalAgentSession session, List<String> missing);
}
