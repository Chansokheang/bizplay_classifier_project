package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.Bot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRepository extends JpaRepository<Bot, UUID> {

    /** List all bots for a corporation (ordered by name for stable UI display).
     *  corp_no is a soft cross-service reference, so no FK existence is implied. */
    List<Bot> findAllByCorpNoOrderByNameAsc(String corpNo);

    /** Resolve a bot by its linked Telegram bot token. Used during configure to
     *  detect a token already claimed by another bot in this system. */
    Optional<Bot> findByTelegramBotToken(String telegramBotToken);

    /** Lookup that eagerly fetches {@code recommendedQuestions} so the
     *  collection is safe to access after the Hibernate session closes. Used
     *  by the Telegram handler — wrapping the entire poll flow in a
     *  transaction is undesirable (the LLM call inside takes 10-30s and would
     *  hold a DB connection that whole time), so the entity graph approach
     *  loads what we need up front and returns a detached entity. */
    @EntityGraph(attributePaths = "recommendedQuestions")
    Optional<Bot> findWithRecommendedQuestionsById(UUID id);

    /** Bots that currently have an active Telegram link. Used at application
     *  startup by the polling manager to spin up one poller per configured
     *  bot — there is no in-memory state to recover, just the DB rows. */
    List<Bot> findAllByTelegramBotTokenIsNotNull();

    /** Update just the Telegram offset for one bot. Avoids the cost (and
     *  surprise updated_at write) of round-tripping the entire entity on every
     *  successful poll cycle. Each poller calls this after acknowledging each
     *  update so a restart never replays the same update_id twice.
     *
     *  <p>{@code @Transactional} on the repository method itself so it can be
     *  called directly from the polling thread without an outer transaction
     *  scope — the polling loop is intentionally NOT transactional (we don't
     *  want a DB connection pinned during the 30s long-poll wait). */
    @Modifying
    @Transactional
    @Query("UPDATE Bot b SET b.telegramLastOffset = :offset WHERE b.id = :id")
    int updateTelegramLastOffset(@Param("id") UUID id, @Param("offset") long offset);

    /** Resolve a bot by its Kakao webhook secret — the path-secret on the
     *  inbound Kakao webhook URL IS the credential, so this lookup runs on
     *  every inbound Kakao message. Backed by the partial-unique index
     *  {@code idx_bots_kakao_webhook_secret}. */
    Optional<Bot> findByKakaoWebhookSecret(String kakaoWebhookSecret);
}
