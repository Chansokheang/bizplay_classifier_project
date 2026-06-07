package com.api.bizplay_conversational.service.expenseReportAgentService;

import com.api.bizplay_conversational.model.request.ExpenseReportAgentRequest;
import com.api.bizplay_conversational.model.response.ExpenseReportAgentResponse;
import com.api.bizplay_conversational.model.response.SessionDetailResponse;
import com.api.bizplay_conversational.model.response.SessionSummaryResponse;

import java.util.List;

/**
 * Expense Report ("After Business Trip") Agent. Twin of the Trip Plan Agent, but for the report:
 * a new session is bootstrapped from a finished trip plan, then receipts (PDF) + typed details are
 * turned into expense lines (COST / TRANSPORTATION / ETC) accumulated on the session draft_json.
 */
public interface ExpenseReportAgentService {

    /** List EXPENSE_REPORT sessions for a corp (newest first) so a sessionId can be found and reused. */
    List<SessionSummaryResponse> listSessions(String corpNo);

    /** Get a single session's full detail (draft + chat history) by id. */
    SessionDetailResponse getSession(String sessionId);

    /** One conversation turn: bootstrap (if new) + process receipts/details into the report draft. */
    ExpenseReportAgentResponse chat(ExpenseReportAgentRequest request);
}
