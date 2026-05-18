package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mapping between a Kakao i Openbuilder user (identified by Kakao's stable
 * per-bot user id) and the bot-scoped {@link ChatSession} our app uses to
 * track conversation history. Mirrors {@link TelegramChat} for the Kakao
 * channel.
 *
 * <p>Schema-level integrity:
 *   <ul>
 *     <li>{@code (bot_id, kakao_user_id)} unique — one session per (bot,
 *         user) — so a Kakao user chatting with the same bot resumes the
 *         same conversation across messages.</li>
 *     <li>{@code bot_id} FK ON DELETE CASCADE — bot delete wipes mappings.</li>
 *     <li>{@code session_id} FK ON DELETE CASCADE — clearing the session
 *         (e.g. via a reset command) automatically drops the mapping so the
 *         next message starts a fresh session.</li>
 *   </ul>
 */
@Entity
@Table(name = "kakao_chats")
@Getter
@Setter
@NoArgsConstructor
public class KakaoChat {

    @Id
    private UUID id;

    @Column(name = "bot_id", nullable = false)
    private UUID botId;

    @Column(name = "kakao_user_id", nullable = false, length = 128)
    private String kakaoUserId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
