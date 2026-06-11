package com.api.bizplay_conversational.service.requestBodyBuilderService;

import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.DraftEditPlan;
import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.api.bizplay_conversational.model.response.TravelerResolution;

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
    default TravelerResolution mergeTextAnalysis(ConversationalAgentSession session, TextAnalysisResult analysis) {
        return mergeTextAnalysis(session, analysis, true);
    }

    /**
     * Merge extracted trip information (from the Text Analysis or PDF Agent) into draft_json.
     *
     * @param authoritative when {@code true} (e.g. the user's typed message), present values overwrite
     *                      existing ones; when {@code false} (e.g. a supplementary PDF), only blanks are
     *                      filled so a higher-priority source is never clobbered.
     * @return which extracted traveler names could not be added (not found / ambiguous), for the reply.
     */
    TravelerResolution mergeTextAnalysis(ConversationalAgentSession session, TextAnalysisResult analysis, boolean authoritative);

    /**
     * Merge ONLY the travelers (people + their routes) from an extracted result, ignoring trip-level
     * fields (destination / dates / type / title). Used when an uploaded PDF describes a different
     * trip than the user's authoritative message, but its passengers still belong to this trip.
     * Named travelers are resolved against the staff DB; trip-level details are never touched.
     *
     * @return which extracted traveler names could not be added (not found / ambiguous), for the reply.
     */
    TravelerResolution mergeTravelersOnly(ConversationalAgentSession session, TextAnalysisResult analysis);

    /**
     * When the draft has pending (ambiguous) travelers, try to resolve them from the user's reply
     * (a department / position / name that uniquely identifies one candidate). Adds the chosen
     * travelers to the draft and removes them from the pending list. Returns the names added.
     */
    List<String> resolvePendingTravelers(ConversationalAgentSession session, String message);

    /**
     * Read-only view of the draft's still-pending AMBIGUOUS travelers (a name that matched more than
     * one staff member and is awaiting the user's pick). Used to surface a "choose which one" prompt
     * after an Update Agent edit. Returns an empty resolution when nothing is ambiguous.
     */
    TravelerResolution pendingResolution(ConversationalAgentSession session);

    /** Names of all travelers currently held pending (ambiguous or not-yet-registered), for skip matching. */
    List<String> pendingTravelerNames(ConversationalAgentSession session);

    /**
     * Drop a pending (ambiguous / unresolved) traveler mention so it is no longer treated as a
     * traveler — used when the user declines to add it. Also removes any unresolved placeholder
     * traveler (no staffId) added under that name. Returns the removed name, or null if none matched.
     */
    String removePendingTraveler(ConversationalAgentSession session, String name);

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
