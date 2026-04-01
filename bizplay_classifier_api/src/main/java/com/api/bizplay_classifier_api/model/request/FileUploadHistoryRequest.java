package com.api.bizplay_classifier_api.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileUploadHistoryRequest {
    private UUID companyId;
    private String originalFileName;
    private String storedFileName;
    private String fileUrl;
    private String sheetName;
}
