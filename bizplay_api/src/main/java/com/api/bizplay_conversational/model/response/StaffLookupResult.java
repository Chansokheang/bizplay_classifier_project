package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StaffLookupResult {
    private boolean matched;
    private String corpNo;
    private UUID staffId;
    private String staffName;
    private UUID departmentId;
    private String departmentName;
    private String position;
    private String matchType;
    private String message;
}
