package com.api.bizplay_chatbot.bot.service;

import com.api.bizplay_chatbot.bot.dto.*;
import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.config.AppZone;
import com.api.bizplay_chatbot.config.BotDefaults;
import com.api.bizplay_chatbot.domain.entity.Bot;
import com.api.bizplay_chatbot.domain.entity.BotRecommendedQuestion;
import com.api.bizplay_chatbot.domain.enums.EmbeddingStatus;
import com.api.bizplay_chatbot.domain.repository.BotRecommendedQuestionRepository;
import com.api.bizplay_chatbot.domain.repository.BotRepository;
import com.api.bizplay_chatbot.domain.repository.ChatMessageRepository;
import com.api.bizplay_chatbot.domain.repository.ChatSessionRepository;
import com.api.bizplay_chatbot.domain.repository.DocumentRepository;
import com.api.bizplay_chatbot.telegram.client.TelegramApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * CRUD for bots. Owns the bot delete pipeline — vector chunks (JDBC, by
 * metadata.bot_id), on-disk uploads, and the row deletion (also JDBC, so
 * the JPA persistence context never holds related entities while the bot
 * row is being removed). The DB's ON DELETE CASCADE on documents,
 * chat_sessions, chat_messages, and bot_recommended_questions handles the
 * row removal of related tables.
 *
 * Tenant scope: corp_no is a soft reference to a corporation managed by
 * an external login service. Reads / writes here are NOT corp-scoped —
 * {@link #list()} and id-based lookups span all corps; use
 * {@link #listByCorpNo(String)} for the corp-filtered variant.
 * {@link DefaultCorporationProvider#currentNo()} is still used as the
 * default corp_no when callers omit it on create.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotService {

    private final BotRepository botRepository;
    private final BotRecommendedQuestionRepository recommendedQuestionRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DefaultCorporationProvider corpProvider;
    private final KeywordExtractor keywordExtractor;
    private final Map<String, ChatClient> chatClientRegistry;
    private final JdbcTemplate jdbcTemplate;
    // Low-level Telegram client (NOT TelegramBotService) — injecting the high-
    // level service would create a cycle: BotService → TelegramBotService →
    // ChatService → BotService. The webhook unregister is the only Telegram
    // operation needed during delete, so the low-level client is sufficient.
    private final TelegramApiClient telegramApiClient;

    // ─── Reads ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BotSummary> list() {
        // Returns every bot in the system regardless of corp_id. Tenant
        // boundaries are enforced upstream (login service), not here — corp_id
        // is just a data field on the bot. Use listByCorp(corpId) for the
        // corp-filtered variant.
        return botRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(BotService::toSummary)
                .toList();
    }

    /**
     * Cross-corp listing variant. Unlike {@link #list()} which returns every
     * bot, this filters to a single {@code corp_no}. corp_no is a soft
     * cross-service reference, so we don't verify it exists locally — unknown
     * codes simply return an empty list.
     */
    @Transactional(readOnly = true)
    public List<BotSummary> listByCorpNo(String corpNo) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new BusinessException("corpNo is required", HttpStatus.BAD_REQUEST);
        }
        return botRepository.findAllByCorpNoOrderByNameAsc(corpNo).stream()
                .map(BotService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public BotResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    /**
     * Aggregate counters for a bot. Built entirely from JPA count queries so
     * the call is cheap regardless of history size — no rows are loaded.
     * conversationTurnCount is derived from user-role messages (one user
     * prompt = one turn; the assistant reply is the other half of the same turn).
     *
     * When {@code windowDays} is non-null, the response also carries
     * {@code window*} fields covering the half-open interval
     * {@code [now − windowDays, now)} plus {@code previousWindow*} fields
     * covering the immediately preceding window of the same length, so the
     * dashboard can compute "Up X% compared to the previous period" without a
     * second round-trip. Half-open semantics mean the two windows touch but
     * don't overlap at the {@code windowStart} boundary.
     *
     * "Conversation" here means a session row — the count uses
     * {@code chat_sessions.created_at}, so it's "conversations *started* in the
     * window", not "conversations *active* in the window". A session opened
     * before {@code windowStart} that received messages inside the window is
     * NOT counted as a conversation here, but its messages ARE counted by
     * {@code windowMessageCount}.
     */
    @Transactional(readOnly = true)
    public BotStatistics getStatistics(UUID id, Integer windowDays) {
        Bot bot = loadOrThrow(id);
        long documentCount = documentRepository.countByBotId(id);
        long completed = documentRepository.countByBotIdAndEmbeddingStatus(id, EmbeddingStatus.COMPLETED);
        long processing = documentRepository.countByBotIdAndEmbeddingStatus(id, EmbeddingStatus.PROCESSING);
        long failed = documentRepository.countByBotIdAndEmbeddingStatus(id, EmbeddingStatus.FAILED);
        long sessionCount = chatSessionRepository.countByBotId(id);
        long messageCount = chatMessageRepository.countBySessionBotId(id);
        long userTurnCount = chatMessageRepository.countBySessionBotIdAndRole(id, "user");
        long recommendedCount = recommendedQuestionRepository.countByBotId(id);
        LocalDateTime lastChatAt = chatMessageRepository.findLastChatAtByBotId(id).orElse(null);

        BotStatistics.BotStatisticsBuilder builder = BotStatistics.builder()
                .botId(bot.getId())
                .botName(bot.getName())
                .documentCount(documentCount)
                .completedDocumentCount(completed)
                .processingDocumentCount(processing)
                .failedDocumentCount(failed)
                .chatSessionCount(sessionCount)
                .messageCount(messageCount)
                .conversationTurnCount(userTurnCount)
                .recommendedQuestionCount(recommendedCount)
                .lastChatAt(lastChatAt);

        if (windowDays != null && windowDays > 0) {
            // Compute "now" against the application's pinned zone so the
            // window doesn't drift if the JVM's default zone is wrong (see
            // AppZone javadoc — single source of truth for the analytics
            // dashboard's date math).
            LocalDateTime windowEnd   = LocalDateTime.now(AppZone.APPLICATION_ZONE);
            LocalDateTime windowStart = windowEnd.minusDays(windowDays);
            LocalDateTime prevStart   = windowStart.minusDays(windowDays);

            builder.windowDays(windowDays)
                   .windowStart(windowStart)
                   .windowEnd(windowEnd)
                   .windowConversationCount(
                       chatSessionRepository.countSessionsInWindow(id, windowStart, windowEnd))
                   .previousWindowConversationCount(
                       chatSessionRepository.countSessionsInWindow(id, prevStart, windowStart))
                   .windowMessageCount(
                       chatMessageRepository.countMessagesInWindow(id, windowStart, windowEnd))
                   .previousWindowMessageCount(
                       chatMessageRepository.countMessagesInWindow(id, prevStart, windowStart))
                   .windowTokenUsage(
                       chatMessageRepository.sumTokensInWindow(id, windowStart, windowEnd))
                   .previousWindowTokenUsage(
                       chatMessageRepository.sumTokensInWindow(id, prevStart, windowStart))
                   .channelDistribution(
                       toStringLongMap(chatSessionRepository.countSessionsByChannelInWindow(id, windowStart, windowEnd)))
                   .languageDistribution(
                       toStringLongMap(chatMessageRepository.countUserMessagesByLangInWindow(id, windowStart, windowEnd)));
        }

        return builder.build();
    }

    /**
     * Per-day series feeding the "Daily conversation count" bar chart.
     * Returns one {@link DailyStat} per calendar day inside
     * [now − windowDays, now), zero-filled so the client can render every
     * label without gap-filling. Counts are computed by two single-shot
     * native queries (sessions per day, messages per day), then merged.
     */
    @Transactional(readOnly = true)
    public List<DailyStat> getDailyStatistics(UUID id, int windowDays) {
        if (windowDays <= 0) {
            throw new BusinessException("windowDays must be positive", HttpStatus.BAD_REQUEST);
        }
        loadOrThrow(id); // 404 if missing

        LocalDateTime windowEnd = LocalDateTime.now(AppZone.APPLICATION_ZONE);
        LocalDateTime windowStart = windowEnd.minusDays(windowDays);
        LocalDate fromDay = windowStart.toLocalDate();
        // Last calendar day included in the window. Using windowEnd's date
        // directly so the bar chart includes "today".
        LocalDate toDay   = windowEnd.toLocalDate();

        Map<LocalDate, Long> sessionsByDay = toDayCountMap(
                chatSessionRepository.countSessionsPerDay(id, windowStart, windowEnd));
        Map<LocalDate, Long> messagesByDay = toDayCountMap(
                chatMessageRepository.countMessagesPerDay(id, windowStart, windowEnd));

        // Zero-fill: emit one bucket per day in the inclusive [fromDay, toDay] range.
        List<DailyStat> series = new java.util.ArrayList<>();
        for (LocalDate d = fromDay; !d.isAfter(toDay); d = d.plusDays(1)) {
            series.add(DailyStat.builder()
                    .date(d)
                    .conversationCount(sessionsByDay.getOrDefault(d, 0L))
                    .messageCount(messagesByDay.getOrDefault(d, 0L))
                    .build());
        }
        return series;
    }

    /**
     * Convert {@code (String, Long)} aggregate rows from the channel- /
     * language-distribution queries into an insertion-ordered map (so the UI
     * sees the rows in the order the query returned them — typically count
     * DESC). Skips rows whose key is null or blank to avoid an awkward "" or
     * null slice on the dashboard pie / language bar.
     */
    private static Map<String, Long> toStringLongMap(List<Object[]> rows) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (Object[] row : rows) {
            Object key = row[0];
            if (key == null) continue;
            String k = key.toString();
            if (k.isBlank()) continue;
            Number count = (Number) row[1];
            out.put(k, count.longValue());
        }
        return out;
    }

    /**
     * Top-K domain-specific topic keywords from user messages inside the
     * window. Drives the analytics dashboard's "Popular keywords" chip
     * widget.
     *
     * <p>Implementation: a single LLM topic-extraction call via
     * {@link KeywordExtractor}, using the bot's own configured chat model.
     * Multi-word phrases ("business trip", "travel expense") are produced
     * naturally; generic verbs and conversational filler are filtered by the
     * model rather than by a static stopword list. Returns an empty list on
     * LLM failure — the dashboard treats no-data and extraction-failed
     * identically.
     */
    @Transactional(readOnly = true)
    public List<KeywordStat> getKeywordStatistics(UUID id, int windowDays, int limit) {
        if (windowDays <= 0) {
            throw new BusinessException("windowDays must be positive", HttpStatus.BAD_REQUEST);
        }
        if (limit <= 0 || limit > 100) {
            throw new BusinessException("limit must be in [1, 100]", HttpStatus.BAD_REQUEST);
        }
        Bot bot = loadOrThrow(id); // 404 if missing; carries the LLM model selection

        LocalDateTime windowEnd = LocalDateTime.now(AppZone.APPLICATION_ZONE);
        LocalDateTime windowStart = windowEnd.minusDays(windowDays);

        List<String> userMessages =
                chatMessageRepository.findUserMessageContentsInWindow(id, windowStart, windowEnd);
        return keywordExtractor.extractTopKeywords(bot, userMessages, limit);
    }

    /** Convert raw native-query rows of (java.sql.Date, Long) into a
     *  LocalDate-keyed map. Defensive against driver returning either
     *  {@link java.sql.Date} or {@link LocalDate} for the bucket column. */
    private static Map<LocalDate, Long> toDayCountMap(List<Object[]> rows) {
        Map<LocalDate, Long> out = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day;
            Object bucket = row[0];
            if (bucket instanceof LocalDate ld) {
                day = ld;
            } else if (bucket instanceof Date sqlDate) {
                day = sqlDate.toLocalDate();
            } else {
                day = LocalDate.parse(bucket.toString());
            }
            // COUNT(*) returns Long in Postgres; defensive cast for other drivers.
            Number count = (Number) row[1];
            out.put(day, count.longValue());
        }
        return out;
    }

    /**
     * List every chat session belonging to a bot (newest first), each with
     * its message count and last-activity timestamp. The full transcript for
     * a single session is still served by {@code GET /rag/chat/history/{sessionId}};
     * this endpoint exists so admin dashboards can enumerate sessions without
     * paging through every message. Backed by a single aggregate query — no N+1.
     */
    @Transactional(readOnly = true)
    public List<ChatSessionSummary> listSessions(UUID botId) {
        loadOrThrow(botId); // 404 if the bot doesn't exist
        return chatSessionRepository.findAggregatesByBotId(botId).stream()
                .map(a -> ChatSessionSummary.builder()
                        .sessionId(a.getSessionId())
                        .botId(botId)
                        .createdAt(a.getCreatedAt())
                        .lastMessageAt(a.getLastMessageAt())
                        .messageCount(a.getMessageCount())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecommendedQuestionDto> listRecommendedQuestions(UUID botId) {
        // Confirm the bot exists (404 otherwise). corp_id is no longer enforced
        // here — bots are addressable by id alone.
        loadOrThrow(botId);
        return recommendedQuestionRepository.findAllByBotIdOrderByCreatedAtAsc(botId).stream()
                .map(BotService::toQuestionDto)
                .toList();
    }

    /**
     * Resolve a bot for use by chat/document services. Throws 404 if the bot
     * is missing or owned by a different corporation. Public so other modules
     * can centralise the lookup logic here.
     */
    @Transactional(readOnly = true)
    public Bot loadActiveBot(UUID id) {
        return loadOrThrow(id);
    }

    /**
     * Append a single recommended question to an existing bot. Returns the
     * persisted DTO (with the server-assigned ID populated). The bulk
     * "replace all" path stays on PUT /chatbot/api/v1/bots/{id}; this is for
     * single-item incremental edits from a bot-management UI.
     */
    @Transactional
    public RecommendedQuestionDto addRecommendedQuestion(UUID botId, RecommendedQuestionDto dto) {
        Bot bot = loadOrThrow(botId);
        BotRecommendedQuestion q = new BotRecommendedQuestion();
        q.setBot(bot);
        q.setQuestion(dto.getQuestion());
        BotRecommendedQuestion saved = recommendedQuestionRepository.save(q);
        log.info("Recommended question added: botId={}, questionId={}", botId, saved.getId());
        return toQuestionDto(saved);
    }

    /**
     * Delete a single recommended question. Validates that the question
     * belongs to the bot in the URL — otherwise a caller could delete one
     * bot's question by guessing UUIDs and using a different bot's path.
     */
    @Transactional
    public void deleteRecommendedQuestion(UUID botId, UUID questionId) {
        loadOrThrow(botId); // tenant-scoped existence check
        BotRecommendedQuestion q = recommendedQuestionRepository.findByIdAndBotId(questionId, botId)
                .orElseThrow(() -> new BusinessException(
                        "Recommended question not found: " + questionId, HttpStatus.NOT_FOUND));
        recommendedQuestionRepository.delete(q);
        log.info("Recommended question deleted: botId={}, questionId={}", botId, questionId);
    }

    // ─── Mutations ────────────────────────────────────────────────────────

    @Transactional
    public BotResponse create(BotCreateRequest req) {
        validateModel(req.getLlmModel());
        // corp_no is a soft reference — caller may supply any value (it points
        // at a row owned by the external login service). When omitted we fall
        // back to the configured default corp_no.
        String corpNo = (req.getCorpNo() != null && !req.getCorpNo().isBlank())
                ? req.getCorpNo()
                : corpProvider.currentNo();

        Bot bot = new Bot();
        bot.setCorpNo(corpNo);
        bot.setName(req.getName());
        bot.setDescription(req.getDescription());
        bot.setContactEmail(req.getContactEmail());
        bot.setContactPhone(req.getContactPhone());
        bot.setSystemPrompt(blank(req.getSystemPrompt())
                ? BotDefaults.DEFAULT_SYSTEM_PROMPT
                : req.getSystemPrompt());
        bot.setSourceExpose(req.getSourceExpose() == null ? true : req.getSourceExpose());
        bot.setLlmModel(req.getLlmModel());
        bot.setLlmTemperature(req.getLlmTemperature() == null
                ? BigDecimal.ZERO : req.getLlmTemperature());
        bot.setMaxAnswerLength(req.getMaxAnswerLength() == null ? 2048 : req.getMaxAnswerLength());
        bot.setHistoryTurns(req.getHistoryTurns() == null ? 5 : req.getHistoryTurns());
        bot.setTopK(req.getTopK() == null ? 5 : req.getTopK());

        // Newly-created bots start disabled so the operator can finish setup
        // (upload documents, tune the system prompt, link Telegram) before
        // exposing the bot to traffic. Flip on with PATCH /chatbot/api/v1/bots/{id}/enable.
        bot.setDisabled(true);

        Bot saved = botRepository.save(bot);

        // Recommended questions go through the bot's collection so cascade
        // saves them in one shot.
        if (req.getRecommendedQuestions() != null && !req.getRecommendedQuestions().isEmpty()) {
            replaceRecommendedQuestions(saved, req.getRecommendedQuestions());
            saved = botRepository.save(saved);
        }
        log.info("Bot created: id={}, name=\"{}\", model={}", saved.getId(), saved.getName(), saved.getLlmModel());
        return toResponse(saved);
    }

    @Transactional
    public BotResponse update(UUID id, BotUpdateRequest req) {
        Bot bot = loadOrThrow(id);

        if (req.getName() != null) bot.setName(req.getName());
        if (req.getDescription() != null) bot.setDescription(req.getDescription());
        if (req.getContactEmail() != null) bot.setContactEmail(req.getContactEmail());
        if (req.getContactPhone() != null) bot.setContactPhone(req.getContactPhone());
        if (req.getSystemPrompt() != null) bot.setSystemPrompt(req.getSystemPrompt());
        if (req.getSourceExpose() != null) bot.setSourceExpose(req.getSourceExpose());
        if (req.getLlmModel() != null) {
            validateModel(req.getLlmModel());
            bot.setLlmModel(req.getLlmModel());
        }
        if (req.getLlmTemperature() != null) bot.setLlmTemperature(req.getLlmTemperature());
        if (req.getMaxAnswerLength() != null) bot.setMaxAnswerLength(req.getMaxAnswerLength());
        if (req.getHistoryTurns() != null) bot.setHistoryTurns(req.getHistoryTurns());
        if (req.getTopK() != null) bot.setTopK(req.getTopK());

        // Atomic replacement of recommended questions when the field is present.
        // Null = leave existing alone; empty list = clear them.
        if (req.getRecommendedQuestions() != null) {
            replaceRecommendedQuestions(bot, req.getRecommendedQuestions());
        }

        Bot saved = botRepository.save(bot);
        log.info("Bot updated: id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void disable(UUID id) {
        Bot bot = loadOrThrow(id);
        bot.setDisabled(true);
        botRepository.save(bot);
        log.info("Bot disabled: id={}", id);
    }

    @Transactional
    public void enable(UUID id) {
        Bot bot = loadOrThrow(id);
        bot.setDisabled(false);
        botRepository.save(bot);
        log.info("Bot enabled: id={}", id);
    }

    /**
     * Hard delete with cascade. Done entirely through JDBC so the JPA
     * persistence context never holds Document / ChatSession entities that
     * reference a Bot we're about to remove — Hibernate could otherwise raise
     * TransientObjectException at flush. DB-level ON DELETE CASCADE on the
     * FKs from documents, chat_sessions (→ chat_messages), and
     * bot_recommended_questions handles the row removal when the bot row is
     * deleted; we only need to take care of the side effects (vector chunks
     * and on-disk files) ourselves.
     *
     * Order:
     *   1. Existence check (404 if missing).
     *   2. Disk file cleanup, per document directory.
     *   3. Vector chunks  (filtered by metadata.bot_id).
     *   4. Bot row delete (DB CASCADE wipes the related JPA tables).
     */
    @Transactional
    public void delete(UUID id) {
        if (!botRepository.existsById(id)) {
            throw new BusinessException("Bot not found: " + id, HttpStatus.NOT_FOUND);
        }

        // Telegram cleanup runs BEFORE the row delete so we can still read the
        // token. Best-effort: a deleteWebhook failure (Telegram unreachable,
        // token already invalid) must not block the local delete — the row is
        // about to disappear anyway, leaving no live receiver for further
        // updates.
        String tgToken = jdbcTemplate.query(
                "SELECT telegram_bot_token FROM bots WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                id);
        if (tgToken != null) {
            try {
                telegramApiClient.deleteWebhook(tgToken);
                log.info("Telegram webhook deregistered for botId={}", id);
            } catch (RuntimeException e) {
                log.warn("Telegram deleteWebhook on bot delete failed (continuing): {}",
                        e.getMessage());
            }
        }

        // Just the IDs — never load the Document entity into the session.
        List<UUID> docIds = jdbcTemplate.queryForList(
                "SELECT id FROM documents WHERE bot_id = ?", UUID.class, id);
        for (UUID docId : docIds) {
            deleteFilesUnder(Paths.get("uploads", docId.toString()));
        }

        int vectorRows = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'bot_id' = ?",
                id.toString());

        // ON DELETE CASCADE handles documents, chat_sessions (→ chat_messages),
        // bot_recommended_questions, and (V9) telegram_chats.
        jdbcTemplate.update("DELETE FROM bots WHERE id = ?", id);
        log.info("Bot deleted: id={}, docs={}, vector chunks={}",
                id, docIds.size(), vectorRows);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private Bot loadOrThrow(UUID id) {
        // No corp filter — bots are addressable by id alone (corp_id is a soft
        // reference, not a security boundary).
        return botRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bot not found: " + id, HttpStatus.NOT_FOUND));
    }

    private void validateModel(String llmModel) {
        if (!chatClientRegistry.containsKey(llmModel)) {
            throw new BusinessException(
                    "Unknown LLM model '" + llmModel + "'. Available: " + chatClientRegistry.keySet(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Replace the bot's recommended questions in place. orphanRemoval on the
     * Bot's @OneToMany handles deletion of the previous rows when we clear()
     * the collection; addAll keeps the bot reference intact for cascade save.
     */
    private void replaceRecommendedQuestions(Bot bot, List<RecommendedQuestionDto> incoming) {
        bot.getRecommendedQuestions().clear();
        // Order is preserved by insertion time (createdAt) — the schema no
        // longer has a display_order column. Items are added in the order
        // they appear in the request, so the user-supplied order is honoured.
        for (RecommendedQuestionDto dto : incoming) {
            BotRecommendedQuestion q = new BotRecommendedQuestion();
            q.setBot(bot);
            q.setQuestion(dto.getQuestion());
            bot.getRecommendedQuestions().add(q);
        }
    }

    private void deleteFilesUnder(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) {
                    log.warn("Could not delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk {}: {}", dir, e.getMessage());
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    // ─── Mappers ──────────────────────────────────────────────────────────

    static BotSummary toSummary(Bot bot) {
        return BotSummary.builder()
                .id(bot.getId())
                .corpNo(bot.getCorpNo())
                .name(bot.getName())
                .description(bot.getDescription())
                .llmModel(bot.getLlmModel())
                .disabled(bot.isDisabled())
                .sourceExpose(bot.isSourceExpose())
                .createdAt(bot.getCreatedAt())
                .updatedAt(bot.getUpdatedAt())
                .build();
    }

    static BotResponse toResponse(Bot bot) {
        List<RecommendedQuestionDto> questions = bot.getRecommendedQuestions().stream()
                .map(BotService::toQuestionDto)
                .toList();
        return BotResponse.builder()
                .id(bot.getId())
                .corpNo(bot.getCorpNo())
                .name(bot.getName())
                .description(bot.getDescription())
                .contactEmail(bot.getContactEmail())
                .contactPhone(bot.getContactPhone())
                .systemPrompt(bot.getSystemPrompt())
                .sourceExpose(bot.isSourceExpose())
                .llmModel(bot.getLlmModel())
                .llmTemperature(bot.getLlmTemperature())
                .maxAnswerLength(bot.getMaxAnswerLength())
                .historyTurns(bot.getHistoryTurns())
                .topK(bot.getTopK())
                .disabled(bot.isDisabled())
                .telegramConfigured(bot.getTelegramBotToken() != null)
                .telegramBotUsername(bot.getTelegramBotUsername())
                .telegramConfiguredAt(bot.getTelegramConfiguredAt())
                .kakaoConfigured(bot.getKakaoWebhookSecret() != null)
                .kakaoBotName(bot.getKakaoBotName())
                .kakaoConfiguredAt(bot.getKakaoConfiguredAt())
                .recommendedQuestions(questions)
                .createdAt(bot.getCreatedAt())
                .updatedAt(bot.getUpdatedAt())
                .build();
    }

    static RecommendedQuestionDto toQuestionDto(BotRecommendedQuestion q) {
        return RecommendedQuestionDto.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .build();
    }
}
