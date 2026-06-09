package com.api.bizplay_conversational.service.expenseService;

import com.api.bizplay_conversational.model.request.ReportCreateRequest;
import com.api.bizplay_conversational.model.request.ReportLineUpdateRequest;
import com.api.bizplay_conversational.model.response.ReportBatchDeleteResponse;
import com.api.bizplay_conversational.model.response.ReportLineResponse;
import com.api.bizplay_conversational.model.response.ReportResponse;

import java.util.List;

public interface ExpenseService {

    /** Get a single expense-report line (conversational_trip_report) by its id, with its expense. 404 if absent. */
    ReportLineResponse getById(String id);

    /**
     * Full replace of one report line (by id): rewrites its expense (cost/transportation), section,
     * department, and approval fields. 400 on invalid section/status, 404 if the line does not exist.
     */
    ReportLineResponse update(String id, ReportLineUpdateRequest request);

    /**
     * Update only the approval_status of a report line (by id). 400 on an invalid status, 404 if the
     * line does not exist. Returns the updated line.
     */
    ReportLineResponse updateApprovalStatus(String id, String approvalStatus);

    /** All expense-report lines for a corp (newest first), each with its linked expense. */
    List<ReportLineResponse> getByCorpNo(String corpNo);

    /** Delete one report line (and its linked expense + attachments) by id. 404 if it does not exist. */
    void deleteById(String id);

    /**
     * Delete several report lines by id (each with its linked expense + attachments). Invalid id
     * formats -> 400; ids that don't exist are reported as notFound. Returns a delete summary.
     */
    ReportBatchDeleteResponse deleteByIds(List<String> ids);

    /**
     * Create an actual Business Trip Report from an approved report body: normalize the expense
     * sections into conversational_cost_expense / conversational_transportation_expense /
     * conversational_trip_report rows (+ REPORT attachments), linked to the trip plan and session.
     * If a drafting session is referenced, its status is flipped to POSTED.
     */
    ReportResponse create(ReportCreateRequest request);
}
