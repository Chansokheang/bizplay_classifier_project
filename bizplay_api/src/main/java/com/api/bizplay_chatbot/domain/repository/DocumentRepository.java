package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.Document;
import com.api.bizplay_chatbot.domain.enums.EmbeddingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByEmbeddingStatus(EmbeddingStatus status);

    /** List documents owned by a bot (for the per-bot library view). */
    List<Document> findAllByBotIdOrderByCreatedAtDesc(UUID botId);

    /** Tenant-style lookup — confirms the document belongs to the supplied bot
     *  before allowing read/delete actions. */
    Optional<Document> findByIdAndBotId(UUID id, UUID botId);

    /** Total documents owned by a bot — used by the bot statistics endpoint. */
    long countByBotId(UUID botId);

    /** Documents in a specific embedding state — lets the stats endpoint break
     *  down PROCESSING / COMPLETED / FAILED counts without loading rows. */
    long countByBotIdAndEmbeddingStatus(UUID botId, EmbeddingStatus status);
}
