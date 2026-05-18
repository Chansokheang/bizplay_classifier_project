package com.api.bizplay_classifier_api.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileStorageResponse {
    private String originalFileName;
    private String storedFileName;
    private String fileUrl;
    private Long size;
    private String contentType;
}
