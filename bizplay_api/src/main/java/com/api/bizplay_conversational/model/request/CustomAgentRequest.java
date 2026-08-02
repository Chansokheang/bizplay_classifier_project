package com.api.bizplay_conversational.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Create/update a user-defined sub-agent. */
@Getter
@Setter
public class CustomAgentRequest {
    /** "When to use" — the router matches incoming messages against this. */
    private String description;
    private String prompt;
    /** LLM model name; null/blank = the conversational default/active model. */
    private String model;
    /** Tool keys from the built-in allowlist (e.g. ["db-select","geocode"]). */
    private List<String> tools;
    private Boolean enabled;
    /** Test endpoint only: the message to run. */
    private String message;
}
