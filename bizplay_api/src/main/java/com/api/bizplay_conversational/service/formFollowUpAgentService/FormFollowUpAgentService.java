package com.api.bizplay_conversational.service.formFollowUpAgentService;

import java.util.List;

/**
 * Follow-up Question sub-agent: when required form fields are still empty, phrase ONE friendly
 * question asking the user for them, using the retrieved form's own labels. Falls back to a
 * deterministic sentence when the model is unavailable.
 */
public interface FormFollowUpAgentService {

    /** @param korean true = ask in Korean; false = ask in English (labels keep their own language). */
    String composeFollowUp(String paperName, List<String> missingLabels, boolean korean);
}
