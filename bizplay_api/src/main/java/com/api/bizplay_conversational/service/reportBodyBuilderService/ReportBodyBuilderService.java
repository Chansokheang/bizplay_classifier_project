package com.api.bizplay_conversational.service.reportBodyBuilderService;

import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.TripReportDraft;
import com.api.bizplay_conversational.model.response.ExpenseAnalysisResult;
import com.api.bizplay_conversational.model.response.PlanResponse;

/**
 * Builds and accumulates the expense-report draft_json on an EXPENSE_REPORT session, mirroring
 * {@code RequestBodyBuilderService} for trip plans. Data-only: the agents extract/analyze, this
 * service deterministically constructs the draft.
 */
public interface ReportBodyBuilderService {

    /**
     * Seed a fresh report draft from a finished trip plan (top block only: CorpNo, PlanType,
     * TripInformation incl. travelers). The expense sections start empty. Trip data is referenced
     * (TripPlanId) and copied for display, never re-derived.
     */
    void bootstrapFromPlan(ConversationalAgentSession session, PlanResponse plan);

    /**
     * Merge analyzed expense lines into the draft's COST / TRANSPORTATION / ETC sections, assigning
     * sequence numbers, and attach the source receipt (if any) to each section it contributed to.
     *
     * @param sourceFileId the receipt fileId these lines came from (may be null for text-only)
     * @return the number of lines merged
     */
    int mergeExpenseAnalysis(ConversationalAgentSession session, ExpenseAnalysisResult analysis, String sourceFileId);

    /** Record a file as a top-level report attachment (deduped). */
    void mergeAttachment(ConversationalAgentSession session, String fileId);

    /** Current typed draft snapshot. */
    TripReportDraft snapshot(ConversationalAgentSession session);

    /** Record the validator's missing-field list onto the draft. */
    void stampMissingFields(ConversationalAgentSession session, java.util.List<String> missing);
}
