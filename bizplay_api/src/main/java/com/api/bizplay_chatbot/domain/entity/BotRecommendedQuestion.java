package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A canned starter prompt shown by the UI alongside a Bot. Owned by exactly
 * one Bot; deleted when its parent bot is deleted.
 *
 * No explicit display-order column — order is preserved by insertion time
 * (createdAt ASC). Caller-supplied order in BotCreateRequest /
 * BotUpdateRequest is honoured because BotService persists items in the order
 * they appear in the incoming list.
 */
@Entity
@Table(name = "bot_recommended_questions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class BotRecommendedQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    @Column(nullable = false, length = 500)
    private String question;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
