package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {
    private String fileId;
    private String filename;
    private long size;
}
