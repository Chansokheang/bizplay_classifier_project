package com.api.bizplay_conversational.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class TripPlanAgentRequest {

    @Schema(example = "1234567890")
    @NotBlank
    @Size(max = 50)
    private String corpNo;

    /** Optional when fileId is provided (a file-only turn needs no text). */
    @Schema(example = """
            For this Toronto business trip, the purpose is quarterly client meetings and partner site visits, and the title is 'Business Trip to Toronto'. \
            The trip runs from 2026-06-20 to 2026-06-25. All travelers depart from Seoul to Toronto, Canada by flight and return to Seoul.
            """)
    private String message;

    /** Existing conversation to continue. Omit to start a new session. */
    @Schema(example = "", description = "Existing session UUID to continue. Omit to start a new session.")
    private String sessionId;

    /**
     * Ids of one or more previously uploaded files (from POST /agent-conversations/files). Each file
     * is routed by its type: Excel (.xlsx/.xls) -> Spreadsheet Agent, PDF (.pdf) -> PDF Agent.
     */
    @Schema(description = "Uploaded file ids. Each is routed by type (Excel -> Spreadsheet Agent, PDF -> PDF Agent).")
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
