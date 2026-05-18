package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    /** Bot-scoped session lookup — guarantees a session ID supplied in a chat
     *  request actually belongs to the bot the request is targeting, so a
     *  client can't smuggle one bot's session into another bot's chat. */
    Optional<ChatSession> findByIdAndBotId(UUID id, UUID botId);

    /** Number of distinct chat sessions ever opened against a bot. Used by the
     *  bot statistics endpoint. */
    long countByBotId(UUID botId);

    /**
     * Sessions whose {@code created_at} falls in the half-open interval
     * {@code [from, to)} for one bot. Drives the windowed "Total
     * conversations" KPI on the analytics dashboard, and the same query is
     * reused for the previous comparable window to compute the % delta.
     *
     * Half-open semantics are deliberate so two adjacent windows
     * ({@code [prevStart, windowStart)} and {@code [windowStart, windowEnd)})
     * cannot double-count a session whose timestamp lands exactly on the
     * shared boundary. Spring Data's {@code Between} keyword would emit
     * inclusive-inclusive {@code BETWEEN}, which leaks at the boundary.
     *
     * "Started in window" semantics: this counts new conversations only.
     * A session opened before {@code from} that received fresh messages
     * inside the window is NOT counted here — see the message-count query
     * for "active in window" coverage.
     */
    @Query("""
        SELECT COUNT(s)
        FROM   ChatSession s
        WHERE  s.bot.id = :botId
          AND  s.createdAt >= :from
          AND  s.createdAt <  :to
    """)
    long countSessionsInWindow(@Param("botId") UUID botId,
                                @Param("from")  LocalDateTime from,
                                @Param("to")    LocalDateTime to);

    /**
     * Per-day session count for the daily-bar-chart series. Returns one row
     * per calendar day with at least one session start; days with zero
     * sessions are filled in by the service layer so the UI can render every
     * label. Native query because JPQL's {@code FUNCTION('DATE', ...)} dispatch
     * is finicky across providers — the underlying {@code DATE(timestamp)}
     * cast is straightforward in Postgres.
     *
     * Returns {@code Object[]} pairs of {@code (java.sql.Date bucket, Long count)}.
     */
    @Query(value = """
        SELECT DATE(s.created_at) AS bucket, COUNT(*) AS c
        FROM   chat_sessions s
        WHERE  s.bot_id = :botId
          AND  s.created_at >= :from
          AND  s.created_at <  :to
        GROUP BY DATE(s.created_at)
        ORDER BY bucket
        """, nativeQuery = true)
    List<Object[]> countSessionsPerDay(@Param("botId") UUID botId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /**
     * One-shot aggregate query used by {@code GET /chatbot/api/v1/bots/{id}/sessions}.
     * Returns one row per session with its per-session message count and the
     * timestamp of the latest message (null if the session has no messages
     * yet). Avoids the N+1 we'd get from looping in the service and asking the
     * message repository per session.
     *
     * Ordered by session creation time, newest first, so admin UIs can render
     * the most recent activity at the top.
     */
    @Query("""
        SELECT s.id           AS sessionId,
               s.createdAt    AS createdAt,
               MAX(m.createdAt) AS lastMessageAt,
               COUNT(m)       AS messageCount
        FROM   ChatSession s
        LEFT JOIN s.messages m
        WHERE  s.bot.id = :botId
        GROUP BY s.id, s.createdAt
        ORDER BY s.createdAt DESC
    """)
    List<SessionAggregate> findAggregatesByBotId(@Param("botId") UUID botId);

    /** Spring Data interface projection feeding the aggregate query above. */
    interface SessionAggregate {
        UUID getSessionId();
        LocalDateTime getCreatedAt();
        LocalDateTime getLastMessageAt();
        Long getMessageCount();
    }

    /**
     * Sessions grouped by channel inside the half-open window. Drives the
     * "Channel distribution" pie on the analytics dashboard. Returns one row
     * per channel actually used in the window — empty channels are absent
     * (the service layer doesn't need to zero-fill since the pie chart
     * only renders slices that exist).
     *
     * Returns {@code Object[]} pairs of {@code (String channel, Long count)}.
     */
    @Query("""
        SELECT s.channel  AS channel,
               COUNT(s)   AS sessionCount
        FROM   ChatSession s
        WHERE  s.bot.id = :botId
          AND  s.createdAt >= :from
          AND  s.createdAt <  :to
        GROUP BY s.channel
        ORDER BY sessionCount DESC
    """)
    List<Object[]> countSessionsByChannelInWindow(@Param("botId") UUID botId,
                                                   @Param("from")  LocalDateTime from,
                                                   @Param("to")    LocalDateTime to);
}
