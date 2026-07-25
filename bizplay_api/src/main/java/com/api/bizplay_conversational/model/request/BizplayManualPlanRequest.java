package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Manual (non-chat) plan creation from the demo UI's form. The backend rebuilds the ③-shaped
 * draft from the RETRIEVED form skeleton and writes these values through the same
 * {@code FormValueWriterService} paths the agent uses — the resulting JSON always matches the
 * dynamic form structure, never a hand-rolled shape.
 */
@Getter
@Setter
public class BizplayManualPlanRequest {

    /** Drafting corporation user (e.g. "30447"). */
    private String corpUserId;
    private long purposeId;
    private Long segmentId;

    private String title;
    private String content;
    /** YYYY-MM-DD. */
    private String startDate;
    /** YYYY-MM-DD. */
    private String endDate;
    private String destination;

    /** Travelers as BizPlay corporationUserIds — one save-body document per traveler. */
    private List<Long> travelerCorpUserIds;

    /**
     * Custom item values keyed by field key ("item:11505"), shaped as the field-mapper would emit
     * them: a plain string, or {@code {"choice": "<option>"}} for option items.
     */
    private JsonNode itemValues;

    /** "Set approval order" picks: [{corporationUserId, approvalKindType?}]. */
    private JsonNode approvalLines;
}
