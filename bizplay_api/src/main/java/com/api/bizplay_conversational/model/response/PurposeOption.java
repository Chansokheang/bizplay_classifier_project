package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One selectable Travel-Purpose × Trip-Type (segment) pair, flattened from the BizPlay purpose
 * catalog (①). This is the unit the user chooses; {@code segmentId} may be null for purposes that
 * define no segments. {@code label} is what the UI chip shows, e.g. "해외출장 · 장기".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PurposeOption {
    private long purposeId;
    private Long segmentId;
    private String purposeName;
    private String segmentName;
    private String label;
    /** Text the UI sends as the next chat message to pick this option. */
    private String sendText;
}
