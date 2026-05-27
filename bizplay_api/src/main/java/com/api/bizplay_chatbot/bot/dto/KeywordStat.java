package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * One entry in the "Popular keywords" widget on the analytics dashboard.
 * Surfaced by {@code GET /chatbot/api/v1/bots/{id}/statistics/keywords} as a
 * frequency-sorted list across all user messages in the requested window.
 */
@Data
@Builder
@Schema(description = "A keyword + how many times it appeared across user messages in the window.")
public class KeywordStat {

    @Schema(description = "The keyword. For Korean tokens, common postpositions "
            + "(은/는/이/가/을/를/의/에/에서/으로/로/와/과/도/들) have been stripped from "
            + "the end so e.g. \"비용을\" and \"비용이\" both contribute to a single \"비용\" bucket. "
            + "English tokens are lowercased.",
            example = "출장")
    private String keyword;

    @Schema(description = "Number of user messages in the window that contained this keyword.",
            example = "32")
    private long count;
}
