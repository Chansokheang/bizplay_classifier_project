package com.api.bizplay_chatbot.bot.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BotResponse {

    private UUID id;
    private String corpNo;
    private String name;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private String systemPrompt;
    private boolean sourceExpose;
    private String llmModel;
    private BigDecimal llmTemperature;
    private int maxAnswerLength;
    private int historyTurns;
    private int topK;
    private boolean disabled;
    /** True iff the bot is currently linked to a Telegram bot. The token itself
     *  is intentionally never serialized — callers only need to know whether
     *  the integration is configured. */
    private boolean telegramConfigured;
    /** Telegram-side handle of the linked bot, e.g. "BizPlayBot". Null when the
     *  bot is not linked. Useful for building the t.me/{username} link in the UI. */
    private String telegramBotUsername;
    private LocalDateTime telegramConfiguredAt;

    /** True iff the bot has a Kakao i Openbuilder Skill webhook configured.
     *  The webhook URL (containing the per-bot secret) is NOT exposed here —
     *  callers use GET /chatbot/api/v1/bots/{id}/kakao to fetch it on demand. */
    private boolean kakaoConfigured;
    /** Operator-supplied display name of the Kakao bot (e.g. "출장규정봇").
     *  Surfaced in UI lists; not used by the webhook protocol itself. */
    private String kakaoBotName;
    private LocalDateTime kakaoConfiguredAt;

    private List<RecommendedQuestionDto> recommendedQuestions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
