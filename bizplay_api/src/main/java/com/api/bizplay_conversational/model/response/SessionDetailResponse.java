package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SessionDetailResponse {
    private UUID sessionId;
    private String corpNo;
    private String agentType;
    private String status;
    private JsonNode draftJson;
    private JsonNode chatEventJson;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
