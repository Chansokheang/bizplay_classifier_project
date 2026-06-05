package com.api.bizplay_conversational.service.requiredFieldValidationService;

import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.MissingFieldsResult;

/**
 * Deterministic "Required Field Prep" gate from the flow: inspects the accumulated trip-plan draft
 * and reports which required fields are still missing. Pure function — it does not touch the session
 * or call any LLM; the Clarification Agent turns its output into a natural-language follow-up.
 */
public interface RequiredFieldValidationService {

    /** Compute the still-missing required fields for the given draft. */
    MissingFieldsResult validate(TripPlanDraft draft);
}
