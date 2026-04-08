package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileClassifySummaryDTO {
    private UUID summaryId;
    private UUID fileId;
    private UUID companyId;
    private Integer totalRows;
    private Integer processedRows;
    private Integer skippedRows;
    private Integer ruleMatchedRows;
    private Integer aiMatchedRows;
    private Integer unmatchedRows;
    private Timestamp createdDate;
    private Timestamp updatedDate;
}
