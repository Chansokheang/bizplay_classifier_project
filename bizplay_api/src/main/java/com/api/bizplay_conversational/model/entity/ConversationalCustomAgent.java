package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A user-defined sub-agent (created at runtime via /custom-agents, per corp). It owns a system
 * prompt, an optional model override, and a comma-separated allowlist of built-in read-only tools.
 * The orchestrator routes a chat turn to it when the message matches its {@code description}
 * ("when to use").
 */
@Getter
@Setter
@NoArgsConstructor
public class ConversationalCustomAgent {

    private String corpNo;
    private String name;
    /** "When to use" — the router matches incoming messages against this. */
    private String description;
    private String prompt;
    /** LLM model name (registry key); null = the conversational default/active model. */
    private String model;
    /** Comma-separated tool keys from the built-in allowlist (e.g. "db-select,geocode"). */
    private String tools;
    private boolean enabled;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
