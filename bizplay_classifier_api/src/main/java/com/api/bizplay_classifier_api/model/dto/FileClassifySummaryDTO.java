package com.api.bizplay_classifier_api.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonIgnore
    private String companyId;
    private Integer totalRows;
    private Integer processedRows;
    private Integer skippedRows;
    private Integer ruleMatchedRows;
    private Integer aiMatchedRows;
    private Integer unmatchedRows;
    private Timestamp createdDate;
    private Timestamp updatedDate;

    @JsonProperty("corpNo")
    public String getCorpNo() {
        return companyId;
    }

    public void setCorpNo(String corpNo) {
        this.companyId = corpNo;
    }
}
