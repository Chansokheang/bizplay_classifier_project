package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Output of the Purpose & Segment Agent: either a confident single {@code resolved} option, or a
 * {@code candidates} list the user must choose from (rendered as chips). {@code reason} is a short
 * agent explanation for logging/UX.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PurposeResolutionResult {
    private PurposeOption resolved;
    private List<PurposeOption> candidates;
    private String reason;

    public boolean isResolved() {
        return resolved != null;
    }
}
