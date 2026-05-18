package com.api.bizplay_classifier_api.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DataTrainSummaryResponse {
    private int totalRows;
    private int trainedRows;
    private int skippedRows;
    private int createdRules;
    private int createdCategories;
    private int createdMappings;
}

