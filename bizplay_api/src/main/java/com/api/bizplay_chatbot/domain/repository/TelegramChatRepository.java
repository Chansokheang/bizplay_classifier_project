package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.TelegramChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TelegramChatRepository extends JpaRepository<TelegramChat, UUID> {

    Optional<TelegramChat> findByBotIdAndChatId(UUID botId, long chatId);

    /** Used by the {@code /reset} command — drop the mapping so the next Telegram
     *  message creates a brand-new ChatSession via ChatService.chat(...). */
    @Modifying
    @Query("DELETE FROM TelegramChat tc WHERE tc.botId = :botId AND tc.chatId = :chatId")
    int deleteByBotIdAndChatId(@Param("botId") UUID botId, @Param("chatId") long chatId);
}
