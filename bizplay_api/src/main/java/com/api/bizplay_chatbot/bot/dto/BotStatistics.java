package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate counters for a single bot — drives the statistics endpoint used by
 * admin/analytics dashboards. All counts are derived from JPA count queries
 * (no row-loading) so the call stays cheap even for bots with large histories.
 */
@Data
@Builder
@Schema(description = "Aggregate counters describing how much content and chat traffic a bot has accumulated.")
public class BotStatistics {

    @Schema(description = "Bot ID the statistics belong to.")
    private UUID botId;

    @Schema(description = "Bot name (denormalised here so callers don't need a second GET /bots/{id} call).")
    private String botName;

    @Schema(description = "Total documents owned by this bot, regardless of embedding status.")
    private long documentCount;

    @Schema(description = "Documents whose embedding pipeline has completed successfully.")
    private long completedDocumentCount;

    @Schema(description = "Documents currently being embedded (PROCESSING state).")
    private long processingDocumentCount;

    @Schema(description = "Documents whose embedding pipeline failed — usually require re-upload.")
    private long failedDocumentCount;

    @Schema(description = "Number of distinct chat sessions ever opened against this bot.")
    private long chatSessionCount;

    @Schema(description = "Total chat messages persisted for this bot (user + assistant combined).")
    private long messageCount;

    @Schema(description = "Conversation turns — one user prompt + its assistant reply counts as a single turn, "
            + "so this equals the number of user-role messages.")
    private long conversationTurnCount;

    @Schema(description = "Number of recommended starter questions configured on the bot.")
    private long recommendedQuestionCount;

    @Schema(description = "Timestamp of the most recent chat message (user or assistant). "
            + "Null if the bot has never been chatted with.")
    private LocalDateTime lastChatAt;

    // ─── Optional time-windowed metrics ──────────────────────────────────────
    // Populated only when the caller passes ?windowDays=N to GET /bots/{id}/statistics.
    // All-null when the caller asks for lifetime totals only.

    @Schema(description = "Echo of the requested window size in days (7 / 30 / 90 …). "
            + "Null when the caller did not ask for a windowed breakdown.")
    private Integer windowDays;

    @Schema(description = "Inclusive lower bound of the requested window. Null if no window requested.")
    private LocalDateTime windowStart;

    @Schema(description = "Exclusive upper bound of the requested window (≈ now at request time). "
            + "Null if no window requested.")
    private LocalDateTime windowEnd;

    @Schema(description = "Sessions started inside the window. Null if no window requested.")
    private Long windowConversationCount;

    @Schema(description = "Sessions started inside the *previous* comparable window — i.e. "
            + "[windowStart - windowDays, windowStart). Lets the UI compute the "
            + "\"Up X% compared to the previous period\" delta. Null if no window requested.")
    private Long previousWindowConversationCount;

    @Schema(description = "Messages persisted inside the window. Null if no window requested.")
    private Long windowMessageCount;

    @Schema(description = "Messages persisted inside the previous comparable window. "
            + "Null if no window requested.")
    private Long previousWindowMessageCount;

    @Schema(description = "Sum of input + output tokens recorded on assistant messages inside the window. "
            + "Drives the dashboard's \"Token usage\" KPI. Zero for windows where the LLM did not return "
            + "a usage block. Null if no window requested.")
    private Long windowTokenUsage;

    @Schema(description = "Same metric for the previous comparable window — lets the UI compute the "
            + "\"Up X% compared to the previous period\" delta on the token-usage card. "
            + "Null if no window requested.")
    private Long previousWindowTokenUsage;

    @Schema(description = "Session counts grouped by channel inside the window — drives the "
            + "\"Channel distribution\" pie chart. Keys are channel codes as written by clients "
            + "(e.g. \"web\", \"telegram\", \"kakaotalk\"); values are session counts. Empty map "
            + "for windows with no activity. Null if no window requested.")
    private Map<String, Long> channelDistribution;

    @Schema(description = "User-message counts grouped by detected language inside the window — drives "
            + "the \"Distribution of languages used\" widget. Keys are BCP-47-ish two-letter codes "
            + "(e.g. \"ko\", \"en\"); values are message counts. Restricted to user messages so the "
            + "assistant's echoed language doesn't double-count. Null if no window requested.")
    private Map<String, Long> languageDistribution;
}
