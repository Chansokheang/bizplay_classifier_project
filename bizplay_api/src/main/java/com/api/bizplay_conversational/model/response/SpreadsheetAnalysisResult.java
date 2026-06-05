package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data-only output of the Spreadsheet Agent. The agent reads a staff-list spreadsheet with
 * unspecified headers, infers the column meanings via the LLM, resolves each row against the
 * staff table, and reports the results. It does NOT touch draft_json — the RequestBody builder
 * merges this. Warnings + unresolved rows support the human-in-the-loop step.
 */
@Getter
@Builder
public class SpreadsheetAnalysisResult {

    /** How the agent mapped the sheet's columns, e.g. {"name": 0, "department": 2, "position": 1}. */
    private Map<String, Integer> columnMapping;

    /** Total data rows considered (excludes the header and skipped blank rows). */
    private int totalRows;

    /** Rows whose name resolved to a staff record. */
    @Builder.Default
    private List<ResolvedStaff> matched = new ArrayList<>();

    /** Names that were extracted from rows but did NOT resolve to any staff record. */
    @Builder.Default
    private List<String> unmatched = new ArrayList<>();

    /** Non-fatal issues (ambiguous columns, skipped sheets/rows, parse hints) for the user. */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Getter
    @Builder
    public static class ResolvedStaff {
        private int rowNumber;          // 1-based row index in the sheet
        private String extractedName;   // raw name read from the cell
        private boolean matched;
        private String staffId;
        private String staffName;
        private String departmentName;
        private String position;
        private String matchType;       // EXACT / PARTIAL / NONE
    }
}
