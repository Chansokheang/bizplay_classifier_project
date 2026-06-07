package com.api.bizplay_conversational.service.expenseAnalysisAgentService;

import com.api.bizplay_conversational.model.response.ExpenseAnalysisResult;
import com.api.bizplay_conversational.model.response.ReceiptExtractionResult;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Expense Analysis Agent (qwen3). Second stage of the receipt pipeline: reconciles the Receipt
 * Extraction Agent's raw fields with the user's typed details, routes the expense into a section
 * (COST / TRANSPORTATION / ETC), and emits structured lines that map 1:1 to expense draft details.
 * Data-only — it never touches draft_json.
 */
public interface ExpenseAnalysisAgentService {

    /**
     * Produce structured, section-routed expense lines.
     *
     * @param extraction raw receipt fields from the Gemma extraction stage (may be empty/no-content)
     * @param userMessage the user's typed details for this turn (may be null)
     * @param history     prior conversation turns for context (may be null)
     * @return structured expense lines (possibly empty)
     */
    ExpenseAnalysisResult analyze(ReceiptExtractionResult extraction, String userMessage, List<Message> history);
}
