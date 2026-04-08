package com.api.bizplay_classifier_api.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileRowPatchResponse {
    private UUID fileId;
    private int totalRequested;
    private int updatedRows;
    /** Row indexes (1-based) that were skipped because the supplied usageCode was not found in the company's categories. */
    private List<Integer> skippedRows;
    private String enrichedFileUrl;
    private String storedFileName;
}
