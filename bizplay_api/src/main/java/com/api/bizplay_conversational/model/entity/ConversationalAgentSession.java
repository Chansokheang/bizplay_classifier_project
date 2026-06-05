package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ConversationalAgentSession {
    private UUID id;
    private String corpNo;
    private AgentType agentType;
    private AgentStatus status = AgentStatus.COLLECTING;
    private JsonNode draftJson = JsonNodeFactory.instance.objectNode();
    private JsonNode chatEventJson = JsonNodeFactory.instance.arrayNode();
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = AgentStatus.COLLECTING;
        }
        if (draftJson == null) {
            draftJson = JsonNodeFactory.instance.objectNode();
        }
        if (chatEventJson == null) {
            chatEventJson = JsonNodeFactory.instance.arrayNode();
        }
    }

    public enum AgentType {
        TRIP_PLAN,
        EXPENSE_REPORT
    }

    public enum AgentStatus {
        COLLECTING,
        READY_FOR_REVIEW,
        APPROVED,
        POSTED,
        CANCELLED
    }
}
