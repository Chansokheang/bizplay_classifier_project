package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-only output of the Expense Analysis Agent (qwen3). It reconciles the Receipt Extraction
 * Agent's raw fields with the user's typed details and produces structured expense LINES, each
 * routed to a report section (COST / TRANSPORTATION / ETC). Each {@link Line} maps 1:1 to an
 * {@code ExpenseDetailDraft}; the ReportBodyBuilder merges them into draft_json.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpenseAnalysisResult {

    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Line {
        /** Section routing: one of "COST", "TRANSPORTATION", "ETC". */
        private String section;

        private String taxCode;
        /** Expense category / kind (e.g. "Hotel", "Flight", "Meal"). Maps to Detail.Type/Category. */
        private String category;
        private String type;
        private String use;
        private String account;
        private String budgetDepartment;

        // Transportation-specific
        private String transportationMethod;
        private String origin;
        private String destination;

        // Dates (YYYY-MM-DD)
        private String startDate;
        private String endDate;
        private String usageDate;
        private String proofDate;

        private String description;
        private String vendor;

        // Money
        private BigDecimal supplyPrice;
        private BigDecimal tax;
        private BigDecimal amountUsed;
        private BigDecimal regulatedAmount;

        private String applicationAmountReasonForExcess;
        private String briefs;
        private String note;
        private String approvalNumber;
    }
}
