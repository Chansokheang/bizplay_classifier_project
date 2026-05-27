package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** BCP-47-ish two-letter language code (e.g. {@code "ko"}, {@code "en"}).
     *  Detected from {@link #content} at insert time. Nullable: rows created
     *  before V5 migration ran are excluded from the distribution rather than
     *  defaulted to a single language. */
    @Column(name = "lang", length = 2)
    private String lang;

    /** Prompt-side token count returned by the LLM (system + history +
     *  retrieved context + user query, tokenized as a single batch).
     *  Persisted only on the assistant message — the user message keeps this
     *  null because the LLM does not tokenize it in isolation. */
    @Column(name = "input_tokens")
    private Integer inputTokens;

    /** Completion-side token count returned by the LLM. Persisted only on
     *  the assistant message. Null when the model's response did not carry
     *  a usage block (some vLLM configurations omit it). */
    @Column(name = "output_tokens")
    private Integer outputTokens;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
