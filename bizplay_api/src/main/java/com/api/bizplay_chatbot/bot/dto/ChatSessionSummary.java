package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compact summary of a chat session, surfaced by
 * {@code GET /chatbot/api/v1/bots/{id}/sessions} so admin / dashboard UIs can list
 * a bot's conversations without paging through every message. To inspect
 * a single session's full transcript, follow up with
 * {@code GET /chatbot/api/v1/rag/chat/history/{sessionId}}.
 */
@Data
@Builder
@Schema(description = "Compact summary of a single chat session belonging to a bot.")
public class ChatSessionSummary {

    @Schema(description = "Session ID. Use it with GET /chatbot/api/v1/rag/chat/history/{sessionId} "
            + "to fetch the full transcript.")
    private UUID sessionId;

    @Schema(description = "Owning bot ID — echoes the path parameter for convenience.")
    private UUID botId;

    @Schema(description = "When the session was created (typically equals the timestamp "
            + "of the first user message).")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp of the most recent message in this session. "
            + "Null if the session has no messages yet.")
    private LocalDateTime lastMessageAt;

    @Schema(description = "Total messages persisted for this session (user + assistant combined).")
    private Long messageCount;
}
