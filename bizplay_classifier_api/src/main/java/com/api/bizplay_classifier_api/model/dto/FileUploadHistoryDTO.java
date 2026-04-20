package com.api.bizplay_classifier_api.model.dto;

import com.api.bizplay_classifier_api.model.enums.FileType;
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
public class FileUploadHistoryDTO {
    private UUID fileId;
    private String companyId;
    private String originalFileName;
    private String storedFileName;
    private String fileUrl;
    private String sheetName;
    private FileType fileType;
    private Timestamp createdDate;
}
