package com.api.bizplay_conversational.service.expenseService;

import com.api.bizplay_conversational.model.request.ReportCreateRequest;
import com.api.bizplay_conversational.model.response.ReportBatchDeleteResponse;
import com.api.bizplay_conversational.model.response.ReportResponse;

import java.util.List;

public interface ExpenseService {

    /** Get a full report (header + all expense lines) by its header id. 404 if absent. */
    ReportResponse getById(String id);

    /** All reports for a corp (newest first), each with its expense lines. */
    List<ReportResponse> getByCorpNo(String corpNo);

    /**
     * Create a report: one header + N expense-line details (cost/transportation), linked to the trip
     * plan and session. The plan must be approved. Returns the assembled report.
     */
    ReportResponse create(ReportCreateRequest request);

    /** Replace an existing report's content (header approval fields + all expense lines) by id. */
    ReportResponse update(String id, ReportCreateRequest request);

    /** Update only the approval_status of a report (by header id). 400 invalid, 404 if absent. */
    ReportResponse updateApprovalStatus(String id, String approvalStatus);

    /** Delete a report (header + its details, expenses, attachments) by id. 404 if absent. */
    void deleteById(String id);

    /**
     * Delete several reports by id. Invalid id formats -> 400; ids that don't exist are reported as
     * notFound. Returns a delete summary.
     */
    ReportBatchDeleteResponse deleteByIds(List<String> ids);
}
