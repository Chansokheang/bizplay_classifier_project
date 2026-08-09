package com.api.bizplay_conversational.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Set up a corp's settlement (출장정산) conversation starter: the chat greeting the hero shows and
 * the example prompts (GPT-style starter rows). Either field may be omitted — a provided value is
 * saved, a blank/empty one resets that piece to the built-in default. Use DELETE to reset both.
 */
@Getter
@Setter
public class SettlementStarterRequest {

    /** The opening message shown on the settlement chat hero. */
    private String greeting;

    /** Example prompts, one per starter row. */
    private List<String> suggestions;
}
