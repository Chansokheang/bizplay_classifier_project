package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

/** One sub-agent module's on/off state for a corp. Fixed modules cannot be turned off. */
@Getter
@Builder
public class AgentModuleResponse {
    private String name;
    private boolean enabled;
    /** Core modules (guardrail, purpose-segment, field-mapper, form-builder) are always on. */
    private boolean fixed;
}
