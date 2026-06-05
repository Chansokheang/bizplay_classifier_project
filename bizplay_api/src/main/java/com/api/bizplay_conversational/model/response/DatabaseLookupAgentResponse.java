package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DatabaseLookupAgentResponse {
    private String task;
    private String sql;
    private boolean executed;
    private String error;
    private List<Map<String, Object>> rows;
}
