package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mapping between a Telegram conversation (identified by Telegram's persistent
 * {@code chat_id}) and the bot-scoped {@link ChatSession} our app uses to track
 * conversation history. One row per (bot, chat_id) — Telegram's chat_id is
 * stable per (telegram bot, telegram user/group), so we can reuse the same
 * ChatSession across messages from the same Telegram conversation and the
 * existing RAG pipeline gets multi-turn history "for free".
 *
 * Schema-level integrity:
 *   - {@code (bot_id, chat_id)} unique — guarantees one mapping per Telegram chat.
 *   - {@code bot_id} FK ON DELETE CASCADE — bot deletion wipes its mappings.
 *   - {@code session_id} FK ON DELETE CASCADE — when a session is removed
 *     elsewhere (e.g. via the {@code /reset} command), the mapping disappears
 *     so the next Telegram message starts a fresh session naturally.
 */
@Entity
@Table(name = "telegram_chats")
@Getter
@Setter
@NoArgsConstructor
public class TelegramChat {

    @Id
    private UUID id;

    @Column(name = "bot_id", nullable = false)
    private UUID botId;

    @Column(name = "chat_id", nullable = false)
    private long chatId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tg_username", length = 64)
    private String tgUsername;

    @Column(name = "tg_first_name", length = 128)
    private String tgFirstName;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
