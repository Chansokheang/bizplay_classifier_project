package com.api.bizplay_classifier_api.model.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DataTrainExecutionResponse {
    private DataTrainSummaryResponse summary;
    private UUID fileId;
    private FileStorageResponse file;
}
