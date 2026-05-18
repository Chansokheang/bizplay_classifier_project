package com.api.bizplay_classifier_api.model.request;

import com.api.bizplay_classifier_api.model.enums.FileType;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @JsonProperty("corpNo")
    @JsonAlias("companyId")
    @Schema(name = "corpNo", example = "1234567890")
    private String companyId;
    private String originalFileName;
    private String storedFileName;
    private String fileUrl;
    private String sheetName;
    private FileType fileType;

    public String getCorpNo() {
        return companyId;
    }

    public void setCorpNo(String corpNo) {
        this.companyId = corpNo;
    }
}
