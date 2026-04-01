package com.api.bizplay_classifier_api.service.storageService;

import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    FileStorageResponse storeFile(MultipartFile file);

    FileStorageResponse storeBytes(byte[] bytes, String originalFileName, String contentType);

    Resource loadAsResource(String storedFileName);
}
