package com.api.bizplay_conversational.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Request for the Expense Report ("After Business Trip") Agent. One chat turn: continue an existing
 * report session (by {@code sessionId}) or start a new one. A NEW session must reference the finished
 * trip plan it reports on via {@code planId}, so the report draft can be bootstrapped from it.
 */
@Data
public class ExpenseReportAgentRequest {

    @Schema(example = "1234567890")
    @NotBlank
    @Size(max = 50)
    private String corpNo;

    /**
     * Id of the trip plan (conversational_trip_plan.id) this report is for. REQUIRED when starting a
     * new session (sessionId omitted); ignored when continuing an existing session.
     */
    @Schema(example = "", description = "Trip plan id to report on. Required when starting a new session.")
    private String planId;

    /** Existing report session UUID to continue. Omit to start a new session. */
    @Schema(example = "", description = "Existing session UUID to continue. Omit to start a new session.")
    private String sessionId;

    /** Ids of uploaded receipt files (from POST /agent-conversations/files). Each PDF is processed in its own pipeline. */
    @Schema(description = "Uploaded receipt file ids. Each PDF is processed by Gemma extraction + qwen3 analysis in parallel.")
    private List<String> fileIds = new ArrayList<>();

    /** File ids, de-duplicated, blanks removed. */
    public List<String> allFileIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (fileIds != null) {
            for (String id : fileIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id.trim());
                }
            }
        }
        return new ArrayList<>(ids);
    }
}
