package com.api.bizplay_chatbot.bot.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compact bot row for listings. Includes the disabled flag so UIs can grey out
 * disabled bots and (optionally) keep them filterable.
 */
@Data
@Builder
public class BotSummary {

    private UUID id;
    /** Soft cross-service reference to the owning corporation by its natural
     *  business code. Included on the summary so admin / cross-tenant UIs can
     *  group or filter bots without an extra {@code GET /bots/{id}} round-trip. */
    private String corpNo;
    private String name;
    private String description;
    private String llmModel;
    private boolean disabled;
    /** Mirrors {@code Bot.sourceExpose}. The testing UI uses this on the
     *  client-side bots list to hide source panels in the chat history when
     *  the current bot has sources turned off. */
    private boolean sourceExpose;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
