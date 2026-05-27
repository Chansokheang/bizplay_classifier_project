package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Total messages across every session of the given bot — drives the
     *  message-count statistic. */
    long countBySessionBotId(UUID botId);

    /** Messages with a particular role across the bot's sessions. Pass "user"
     *  to derive conversation-turn count (one user message = one turn). */
    long countBySessionBotIdAndRole(UUID botId, String role);

    /**
     * Messages whose {@code created_at} falls in the half-open interval
     * {@code [from, to)} for one bot. Drives the windowed "Total messages" KPI
     * and is reused for the previous comparable window to compute the % delta.
     *
     * Half-open semantics match {@link ChatSessionRepository#countSessionsInWindow}
     * so adjacent windows can't double-count a row whose timestamp lands on
     * the shared boundary.
     */
    @Query("""
        SELECT COUNT(m)
        FROM   ChatMessage m
        WHERE  m.session.bot.id = :botId
          AND  m.createdAt >= :from
          AND  m.createdAt <  :to
    """)
    long countMessagesInWindow(@Param("botId") UUID botId,
                                @Param("from")  LocalDateTime from,
                                @Param("to")    LocalDateTime to);

    /** Most recent message timestamp across every session of the given bot.
     *  Empty if the bot has not been chatted with yet. */
    @Query("SELECT MAX(m.createdAt) FROM ChatMessage m WHERE m.session.bot.id = :botId")
    Optional<LocalDateTime> findLastChatAtByBotId(@Param("botId") UUID botId);

    /**
     * Per-day message count within [from, to) for the daily series. Pairs with
     * {@code ChatSessionRepository.countSessionsPerDay} so the service can
     * stitch them into one {@code DailyStat[]}. Native query for the same
     * cross-provider reasons as the sessions variant.
     */
    @Query(value = """
        SELECT DATE(m.created_at) AS bucket, COUNT(*) AS c
        FROM   chat_messages m
        JOIN   chat_sessions s ON s.id = m.session_id
        WHERE  s.bot_id = :botId
          AND  m.created_at >= :from
          AND  m.created_at <  :to
        GROUP BY DATE(m.created_at)
        ORDER BY bucket
        """, nativeQuery = true)
    List<Object[]> countMessagesPerDay(@Param("botId") UUID botId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /**
     * User-message counts grouped by detected language inside the half-open
     * window. Drives the "Distribution of languages used" widget. Restricted
     * to {@code role = 'user'} so the answer's language doesn't double-count
     * (the assistant tracks the user's language by design). Rows with NULL
     * lang (predating V5 migration) are excluded.
     *
     * Returns {@code Object[]} pairs of {@code (String lang, Long count)}.
     */
    @Query("""
        SELECT m.lang   AS lang,
               COUNT(m) AS messageCount
        FROM   ChatMessage m
        WHERE  m.session.bot.id = :botId
          AND  m.role = 'user'
          AND  m.lang IS NOT NULL
          AND  m.createdAt >= :from
          AND  m.createdAt <  :to
        GROUP BY m.lang
        ORDER BY messageCount DESC
    """)
    List<Object[]> countUserMessagesByLangInWindow(@Param("botId") UUID botId,
                                                    @Param("from")  LocalDateTime from,
                                                    @Param("to")    LocalDateTime to);

    /**
     * Sum of input + output tokens persisted on assistant messages inside the
     * window. Drives the "Token usage" KPI card and its previous-window
     * counterpart for the % delta. Returns 0 (not null) when no usage data
     * exists for the window — vLLM-served models with the OpenAI-compat
     * usage block disabled write null tokens, and {@code COALESCE(SUM, 0)}
     * keeps the API surface clean.
     */
    @Query("""
        SELECT COALESCE(SUM(COALESCE(m.inputTokens, 0) + COALESCE(m.outputTokens, 0)), 0)
        FROM   ChatMessage m
        WHERE  m.session.bot.id = :botId
          AND  m.role = 'assistant'
          AND  m.createdAt >= :from
          AND  m.createdAt <  :to
    """)
    long sumTokensInWindow(@Param("botId") UUID botId,
                            @Param("from")  LocalDateTime from,
                            @Param("to")    LocalDateTime to);

    /**
     * Just the {@code content} text of every user message in the window —
     * feeds {@link com.api.bizplay_chatbot.bot.service.KeywordExtractor} for the
     * "Popular keywords" widget. Restricted to user role so the assistant's
     * (much longer) responses don't drown out the user's actual topic words,
     * and projection-only so we don't waste memory loading full ChatMessage
     * entities.
     *
     * <p>Ordered by {@code createdAt DESC} so the LLM extractor can truncate
     * the corpus when it overflows the model's context window without losing
     * the most recent (most representative) topics first.
     */
    @Query("""
        SELECT m.content
        FROM   ChatMessage m
        WHERE  m.session.bot.id = :botId
          AND  m.role = 'user'
          AND  m.createdAt >= :from
          AND  m.createdAt <  :to
        ORDER BY m.createdAt DESC
    """)
    List<String> findUserMessageContentsInWindow(@Param("botId") UUID botId,
                                                  @Param("from")  LocalDateTime from,
                                                  @Param("to")    LocalDateTime to);
}
