package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SessionSummaryResponse {
    private UUID sessionId;
    private String corpNo;
    private String agentType;
    private String status;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
