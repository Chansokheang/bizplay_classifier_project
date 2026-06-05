package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Output of the Required-Field validation gate: which required fields the trip-plan draft is still
 * missing, and whether the draft is complete enough to move on to review/approval.
 *
 * <p>{@code missing} entries are human-readable descriptors (e.g. "trip destination",
 * "origin for John Doe") suitable both for the draft's {@code missingFields} list and as input to
 * the Clarification Agent that phrases the follow-up question.
 */
@Getter
@Builder
public class MissingFieldsResult {

    /** True when no required field is missing (ready for review). */
    private final boolean complete;

    /** Human-readable descriptors of each still-missing required field. */
    private final List<String> missing;
}
