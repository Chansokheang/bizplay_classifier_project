package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A runtime-customized agent prompt (persisted, editable via /agent-prompts). {@code name} is the
 * agent key (e.g. "field-mapper", "guardrail") or "starter-message" for the chat opener. The row
 * overrides the agent's compiled-in default; deleting it restores the default.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConversationalAgentPrompt {

    /** Tenant scope — customizations are saved and resolved per corporation. */
    private String corpNo;
    private String name;
    private String prompt;
    private boolean enabled;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
