package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class StaffResponse {
    private UUID id;
    private String name;
    private String position;
    private UUID departmentId;
    private String departmentName;
    private String corpNo;
    private LocalDateTime createdAt;
}
