package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DepartmentResponse {
    private UUID id;
    private String corpNo;
    private String name;
    private LocalDateTime createdAt;
}
