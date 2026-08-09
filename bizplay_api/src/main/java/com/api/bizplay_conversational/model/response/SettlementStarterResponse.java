package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The settlement conversation starter a corp will see: the effective greeting and example prompts
 * (custom override when set, else the built-in default), plus which source each is coming from so a
 * settings UI can show "Custom / Default".
 */
@Getter
@Builder
public class SettlementStarterResponse {

    /** Opening message for the settlement chat hero (custom-or-default). */
    private String greeting;

    /** Example prompts, one per starter row (custom-or-default). */
    private List<String> suggestions;

    /** "CUSTOM" when the corp overrode the greeting, else "DEFAULT". */
    private String greetingSource;

    /** "CUSTOM" when the corp overrode the suggestions, else "DEFAULT". */
    private String suggestionsSource;
}
