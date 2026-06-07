package com.api.bizplay_conversational.service.expenseAnalysisAgentService;

import com.api.bizplay_conversational.model.response.ExpenseAnalysisResult;
import com.api.bizplay_conversational.model.response.ReceiptExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * qwen3-backed implementation. Takes the raw receipt extraction plus the user's text and emits a
 * JSON list of section-routed expense lines. Uses {@code /no_think} + {@code stripThink} for the
 * Qwen3 thinking model, like the other conversational sub-agents.
 */
@Slf4j
@Service
public class ExpenseAnalysisAgentServiceImple implements ExpenseAnalysisAgentService {

    private static final String SYSTEM_PROMPT = """
            You convert a business-trip expense (a receipt's raw fields plus the user's notes) into a
            JSON list of structured expense LINES. Return ONLY JSON, no prose, no markdown, no code fences:
            { "lines": [ { ...line... } ] }

            Each line:
            {
              "section": "COST|TRANSPORTATION|ETC",
              "taxCode": "...","category": "...","type": "...","use": "...","account": "...",
              "budgetDepartment": "...",
              "transportationMethod": "...","origin": "...","destination": "...",
              "startDate": "YYYY-MM-DD","endDate": "YYYY-MM-DD","usageDate": "YYYY-MM-DD","proofDate": "YYYY-MM-DD",
              "description": "...","vendor": "...",
              "supplyPrice": 0,"tax": 0,"amountUsed": 0,"regulatedAmount": 0,
              "applicationAmountReasonForExcess": "...","briefs": "...","note": "...","approvalNumber": "..."
            }

            Section routing:
            - TRANSPORTATION: movement between places — flight/airfare, train/KTX, taxi, bus, car. Fill usageDate, origin, destination, transportationMethod, supplyPrice, tax.
            - COST: lodging/accommodation and other trip costs tied to a stay or service period. Fill startDate, endDate, proofDate.
            - ETC: miscellaneous expenses that are neither transport nor lodging (meals, supplies, fees).

            Rules:
            - Prefer the USER'S typed details over the receipt when they conflict (the user is authoritative).
            - Use null for anything genuinely unknown. Do NOT invent tax codes or account codes; leave them null if absent.
            - amountUsed is the actual amount paid. If the user states a limit/regulation, set regulatedAmount; if amountUsed exceeds it, fill applicationAmountReasonForExcess with the stated reason.
            - Amounts are plain numbers (no symbols, no separators). Dates are YYYY-MM-DD.
            - If there is no usable expense at all, return { "lines": [] }.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.expense-analysis-agent.model:qwen3-14b}")
    private String modelName;

    public ExpenseAnalysisAgentServiceImple(Map<String, ChatClient> chatClientRegistry, ObjectMapper objectMapper) {
        this.chatClientRegistry = chatClientRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExpenseAnalysisResult analyze(ReceiptExtractionResult extraction, String userMessage, List<Message> history) {
        boolean hasReceipt = extraction != null && extraction.isHasContent();
        boolean hasMessage = userMessage != null && !userMessage.isBlank();
        if (!hasReceipt && !hasMessage) {
            return new ExpenseAnalysisResult();
        }

        ChatClient client = chatClientRegistry.get(modelName);
        if (client == null) {
            log.warn("Expense analysis model is not configured: {}", modelName);
            return new ExpenseAnalysisResult();
        }

        try {
            String receiptJson;
            try {
                receiptJson = hasReceipt ? objectMapper.writeValueAsString(extraction) : "null";
            } catch (Exception e) {
                receiptJson = "null";
            }

            StringBuilder user = new StringBuilder();
            user.append("Receipt raw fields (from extraction):\n").append(receiptJson).append("\n\n");
            user.append("User's typed details:\n").append(hasMessage ? userMessage.trim() : "(none)");

            List<Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(SYSTEM_PROMPT));
            if (history != null) {
                prompt.addAll(history);
            }
            prompt.add(new UserMessage(user.toString()));

            String raw = client.prompt().messages(prompt).call().content();
            log.info("Expense analysis raw output: {}", raw);

            String json = extractJson(stripThink(raw));
            if (json == null) {
                return new ExpenseAnalysisResult();
            }
            ExpenseAnalysisResult result = objectMapper.readValue(json, ExpenseAnalysisResult.class);
            return result != null ? result : new ExpenseAnalysisResult();
        } catch (Exception e) {
            log.warn("Expense analysis failed, returning no lines: {}", e.getMessage());
            return new ExpenseAnalysisResult();
        }
    }

    /** Remove reasoning blocks emitted by thinking models (e.g. Qwen3 &lt;think&gt;...&lt;/think&gt;). */
    private String stripThink(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)</?think>", "");
        return cleaned.trim();
    }

    /** Extract the first JSON object from the model output, tolerating stray text or fences. */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }
}
