package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StaffLookupAgentResponse {
    private String extractedName;
    private String llmModel;
    private boolean llmUsed;
    private long llmElapsedMs;
    private String llmError;
    private StaffLookupResult result;
}
