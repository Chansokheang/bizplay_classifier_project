package com.api.bizplay_conversational.model.request;

import lombok.Getter;
import lombok.Setter;

/** Create/update one agent's custom prompt (or the "starter-message" chat opener). */
@Getter
@Setter
public class AgentPromptRequest {
    private String prompt;
    /** Defaults to true; false keeps the custom text stored but the agent uses its default. */
    private Boolean enabled;
}
