package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatSession {

    @Id
    private UUID id;

    /** Bot this session belongs to. Sessions are bot-scoped — a session ID is
     *  meaningful only within its bot. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    /** Channel the conversation originated from — drives the analytics
     *  dashboard's channel-distribution pie. Free-form (e.g. {@code "web"},
     *  {@code "telegram"}, {@code "kakaotalk"}, {@code "slack"}); ChatService
     *  defaults this to {@code "web"} when the chat request omits it. */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel = "web";

    /**
     * Tie-breaker on {@code role DESC} matters: when a user message and the
     * assistant reply land in the same flush, both children get auditing
     * {@code prePersist} fired at near-identical wall-clock instants, and on
     * sub-microsecond ties they end up with byte-identical {@code createdAt}.
     * Without the secondary sort the read path would return them in
     * non-deterministic physical-row order. Role DESC works because
     * {@code 'u' > 'a'} — user-then-assistant within a turn.
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, role DESC")
    private List<ChatMessage> messages = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
