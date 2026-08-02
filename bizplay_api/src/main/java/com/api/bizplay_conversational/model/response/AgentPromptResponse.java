package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** One agent's prompt state: the compiled-in default, any custom override, and which one is live. */
@Getter
@Builder
public class AgentPromptResponse {
    private String name;
    /** Built-in default prompt (null for names no agent has registered yet). */
    private String defaultPrompt;
    /** Stored custom prompt, if any. */
    private String customPrompt;
    private Boolean enabled;
    /** "CUSTOM" when the override is live, "DEFAULT" otherwise. */
    private String source;
    /** The prompt the agent actually uses right now. */
    private String effectivePrompt;
    private LocalDateTime updatedDate;
}
