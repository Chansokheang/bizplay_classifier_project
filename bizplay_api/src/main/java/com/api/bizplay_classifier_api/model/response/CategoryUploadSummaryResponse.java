package com.api.bizplay_classifier_api.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryUploadSummaryResponse {
    private int totalRows;
    private int insertedRows;
    private int skippedRows;
    private int alreadyExistedRows;
}
