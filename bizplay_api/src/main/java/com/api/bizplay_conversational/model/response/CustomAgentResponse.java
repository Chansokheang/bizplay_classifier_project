package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** One user-defined sub-agent (definition or test-run result). */
@Getter
@Builder
public class CustomAgentResponse {
    private String name;
    private String description;
    private String prompt;
    private String model;
    private List<String> tools;
    private Boolean enabled;
    private LocalDateTime updatedDate;

    /** Test runs only. */
    private String reply;
    private List<String> toolsUsed;
}
