package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.KakaoChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface KakaoChatRepository extends JpaRepository<KakaoChat, UUID> {

    Optional<KakaoChat> findByBotIdAndKakaoUserId(UUID botId, String kakaoUserId);

    /** Used by an in-chat reset command — drop the mapping so the next
     *  Kakao message creates a brand-new ChatSession. */
    @Modifying
    @Query("DELETE FROM KakaoChat kc WHERE kc.botId = :botId AND kc.kakaoUserId = :kakaoUserId")
    int deleteByBotIdAndKakaoUserId(@Param("botId") UUID botId, @Param("kakaoUserId") String kakaoUserId);
}
