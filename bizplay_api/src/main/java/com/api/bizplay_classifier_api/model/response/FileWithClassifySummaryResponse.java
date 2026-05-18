package com.api.bizplay_classifier_api.model.response;

import com.api.bizplay_classifier_api.model.dto.FileClassifySummaryDTO;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileWithClassifySummaryResponse {
    private FileUploadHistoryDTO file;
    private FileClassifySummaryDTO classifySummary;
}
