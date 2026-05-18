package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * One bucket in the per-day series returned by
 * {@code GET /bots/{id}/statistics/daily}. Drives the "Daily conversation count"
 * bar chart on the analytics dashboard. Days with no activity still appear with
 * zero counts (see {@code BotService.getDailyStatistics} — zero-fill is done
 * server-side so the client can render every label without gap-filling).
 */
@Data
@Builder
@Schema(description = "One day's bucket of activity for a bot.")
public class DailyStat {

    @Schema(description = "The day this bucket covers (server timezone). "
            + "Use as the x-axis label.")
    private LocalDate date;

    @Schema(description = "Sessions whose createdAt falls within this calendar day.")
    private long conversationCount;

    @Schema(description = "Messages (user + assistant combined) whose createdAt falls within this day.")
    private long messageCount;
}
