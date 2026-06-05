package com.api.bizplay_conversational.service.spreadsheetAgentService;

import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;

public interface SpreadsheetAgentService {

    /**
     * Read a staff-list spreadsheet (.xlsx/.xls) with unspecified headers, infer which columns
     * hold the staff name / department / position via the LLM, resolve each row against the
     * staff table for the given corp, and return the results (data only — does not touch draft_json).
     *
     * @param content  the raw spreadsheet bytes
     * @param filename original filename (for logging; may be null)
     */
    SpreadsheetAnalysisResult analyze(String corpNo, byte[] content, String filename);
}
