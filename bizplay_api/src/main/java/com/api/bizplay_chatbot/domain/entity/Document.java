package com.api.bizplay_chatbot.domain.entity;

import com.api.bizplay_chatbot.domain.enums.EmbeddingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks document metadata. The actual vector chunks live in the PGVector
 * vector_store table, referencing this entity's id in their metadata.
 */
@Entity
@Table(name = "documents")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Bot that owns this document. Documents are bot-scoped — only chats with
     *  this bot can retrieve from this document's chunks. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 100)
    private String contentType;

    /** Original file name stored on disk under uploads/{id}/{fileName} */
    @Column(length = 500)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
