package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A purpose-specific chatbot. Carries its own system prompt, LLM model +
 * behaviour settings, and (via {@link #recommendedQuestions}) a list of canned
 * starter prompts surfaced by the UI.
 *
 * {@code corpNo} is a soft cross-service reference to a corporation managed
 * by an external login service — there is intentionally no JPA association
 * back to {@link Corporation}, and no DB-level FK. Any corp_no is accepted at
 * write time; existence in the local corp table is not required. (The link
 * was migrated from {@code corp_id} to {@code corp_no} in V3.)
 *
 * Per-bot LLM settings (model / temperature / max-tokens / history-turns / top-K)
 * are absolute at chat time — the chat request cannot override them.
 */
@Entity
@Table(name = "bots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Soft reference to the owning corporation by its natural business
     *  identifier ({@code corp.corp_no}). Authoritative corp data (name,
     *  contacts, billing, etc.) lives in the external login service; here
     *  we only persist the identifier as a plain VARCHAR(50). */
    @Column(name = "corp_no", nullable = false, length = 50)
    private String corpNo;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    /** May be null. ChatService falls back to BotDefaults.DEFAULT_SYSTEM_PROMPT
     *  at chat time when this is null/blank. */
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /** When false, sources are stripped from chat responses for this bot. */
    @Column(name = "source_expose", nullable = false)
    private boolean sourceExpose = true;

    /** Registry key into chatClientRegistry (e.g. "exaone-3.5-7.8b"). */
    @Column(name = "llm_model", nullable = false, length = 100)
    private String llmModel;

    @Column(name = "llm_temperature", nullable = false, precision = 3, scale = 2)
    private BigDecimal llmTemperature = BigDecimal.ZERO;

    /** maxTokens for the LLM call. */
    @Column(name = "max_answer_length", nullable = false)
    private int maxAnswerLength = 2048;

    /** Number of prior turns (user+assistant pairs) included in the prompt. */
    @Column(name = "history_turns", nullable = false)
    private int historyTurns = 5;

    /** Number of chunks retrieved per chat (similarity search topK). */
    @Column(name = "top_k", nullable = false)
    private int topK = 5;

    /** Disabled bots block new chat calls but keep all data. */
    @Column(name = "is_disabled", nullable = false)
    private boolean disabled = false;

    // ─── Telegram channel integration (V9 / V10) ────────────────────────────
    // Null token = bot is not linked to any Telegram bot. When set, the app
    // runs a per-bot polling thread that pulls updates from Telegram via
    // getUpdates (long polling) — there is no inbound webhook. Tokens are
    // stored plain; they MUST never be returned in API responses (see
    // BotResponse — only `telegramConfigured: true` is exposed).
    //
    // The V9 `telegram_webhook_secret` column is intentionally NOT mapped on
    // this entity anymore. It exists in the DB schema for backward-compat
    // but is dead data — long polling has no inbound endpoint to authenticate.

    @Column(name = "telegram_bot_token", length = 128)
    private String telegramBotToken;

    /** Telegram-side handle of the linked bot (e.g. "BizPlayBot"), populated
     *  from getMe at configure time so callers don't need a second round-trip
     *  to display it. */
    @Column(name = "telegram_bot_username", length = 64)
    private String telegramBotUsername;

    /** Highest update_id we've successfully processed from Telegram. Passed
     *  back on every getUpdates call as the {@code offset} parameter so
     *  Telegram knows which updates have been acknowledged and not to replay
     *  them. Null on first poll = "fresh start, fetch everything queued".
     *  Persisted on every successful update so a restart doesn't replay the
     *  last 24h of Telegram's retention window. */
    @Column(name = "telegram_last_offset")
    private Long telegramLastOffset;

    @Column(name = "telegram_configured_at")
    private LocalDateTime telegramConfiguredAt;

    // ─── KakaoTalk channel integration (V11) ────────────────────────────────
    // Unlike Telegram (long polling), Kakao i Openbuilder only supports
    // webhooks. Each bot has a unique webhook URL containing a per-bot
    // secret in the path; operators paste that URL into their Openbuilder
    // Skill configuration. The secret IS the credential — Openbuilder does
    // not natively sign requests, so the URL must not be leaked.

    @Column(name = "kakao_webhook_secret", length = 64)
    private String kakaoWebhookSecret;

    /** Operator-supplied display name of the Kakao i Openbuilder bot this
     *  link points at, e.g. "출장규정봇". For UI display only; Kakao gives
     *  us no canonical name in webhook payloads. */
    @Column(name = "kakao_bot_name", length = 255)
    private String kakaoBotName;

    @Column(name = "kakao_configured_at")
    private LocalDateTime kakaoConfiguredAt;

    @OneToMany(mappedBy = "bot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<BotRecommendedQuestion> recommendedQuestions = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
