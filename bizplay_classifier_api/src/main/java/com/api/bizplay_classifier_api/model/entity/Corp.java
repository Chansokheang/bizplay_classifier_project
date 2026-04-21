package com.api.bizplay_classifier_api.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Corp {
    private Long corpId;
    private Long corpGroupId;
    private String corpNo;
    private UUID userId;
    private String corpName;
    private String businessNumber;
    private String corpGroupCode;
    private Timestamp createdDate;
}

