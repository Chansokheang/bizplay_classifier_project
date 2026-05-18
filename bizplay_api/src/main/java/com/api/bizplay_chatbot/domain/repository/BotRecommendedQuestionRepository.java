package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.BotRecommendedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRecommendedQuestionRepository extends JpaRepository<BotRecommendedQuestion, UUID> {

    /** Insertion order — the schema no longer has a display_order column,
     *  so we order by creation time. */
    List<BotRecommendedQuestion> findAllByBotIdOrderByCreatedAtAsc(UUID botId);

    /** Bot-scoped lookup. Used by the delete endpoint to ensure a question
     *  ID supplied in the URL actually belongs to the bot in the path —
     *  prevents one bot's URL from deleting another bot's question. */
    Optional<BotRecommendedQuestion> findByIdAndBotId(UUID id, UUID botId);

    /** Recommended-question count for the bot statistics endpoint. */
    long countByBotId(UUID botId);
}
