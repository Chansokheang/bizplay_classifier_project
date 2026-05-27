package com.api.bizplay_chatbot.rag.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.UUID;

/**
 * Chat request payload. The bot owns every behavioural setting (LLM model,
 * temperature, max-tokens, history-turns, top-K, source-expose, system prompt)
 * — the request only specifies which bot is being asked, the question, and an
 * optional session ID for multi-turn continuity.
 */
@Getter
public class ChatRequest {

    @Schema(description = "Bot to ask. Required. Must belong to the caller's corporation. "
            + "Disabled bots reject chat with HTTP 409.",
            example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "botId is required")
    private UUID botId;

    @Schema(description = "The question to ask", example = "What is the company leave policy?")
    @NotBlank(message = "Query is required")
    private String query;

    @Schema(description = """
            Chat session identifier. \
            \
            • Omit on the FIRST message of a conversation — the server creates a new session bound to \
            the bot in this request and returns its ID in the response (`data.sessionId`). \
            \
            • Pass that ID back on every subsequent message in the same conversation so the server can \
            load prior turns and use them as conversation history (the bot's `historyTurns` setting \
            controls how many turns are included). Sessions enable referential follow-ups like \
            "explain point 2" or "translate that to Korean" — without a sessionId the model has no \
            memory of the previous turn. \
            \
            • A session is owned by exactly one bot. Reusing a session ID with a different `botId` \
            returns HTTP 400. To start a new conversation, omit the field again. \
            \
            • Persisted: see GET /chatbot/api/v1/rag/chat/history/{sessionId} for retrieval.""",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sessionId;

    @Schema(description = "**Optional.** Channel the conversation originates from. Free-form, but "
            + "the analytics dashboard buckets values into Web / Telegram / KakaoTalk / Others, so "
            + "prefer one of those values when applicable. Persisted on the session row at creation "
            + "time and ignored on subsequent messages of the same session. Defaults to \"web\" when "
            + "omitted, null, or blank.",
            example = "web",
            defaultValue = "web",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 20)
    private String channel;

    public ChatRequest() {}

    /** Programmatic constructor for in-process callers (e.g. the Telegram
     *  channel adapter) that build a request from a different transport rather
     *  than from JSON. Validation annotations don't fire on this path — the
     *  caller is responsible for passing non-null botId and non-blank query. */
    public ChatRequest(UUID botId, String query, UUID sessionId, String channel) {
        this.botId = botId;
        this.query = query;
        this.sessionId = sessionId;
        this.channel = channel;
    }
}
