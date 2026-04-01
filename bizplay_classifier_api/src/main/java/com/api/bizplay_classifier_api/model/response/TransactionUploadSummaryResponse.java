package com.api.bizplay_classifier_api.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionUploadSummaryResponse {
    private java.util.UUID fileId;
    private Integer totalRows;
    private Integer insertedRows;
    private Integer skippedRows;
    private Integer batchSize;
    private String enrichedFileUrl;
    private String storedFileName;
    private Integer ruleMatchedRows;
    private Integer ruleUnmatchedRows;
    private List<String> unmatchedMerchantSamples;
}
