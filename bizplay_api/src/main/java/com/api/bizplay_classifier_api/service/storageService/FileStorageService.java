package com.api.bizplay_classifier_api.service.storageService;

import com.api.bizplay_classifier_api.model.enums.FileType;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    FileStorageResponse storeFile(MultipartFile file);

    FileStorageResponse storeFile(MultipartFile file, FileType fileType);

    FileStorageResponse storeBytes(byte[] bytes, String originalFileName, String contentType);

    FileStorageResponse storeBytes(byte[] bytes, String originalFileName, String contentType, FileType fileType);

    Resource loadAsResource(String storedFileName);

    void deleteByStoredFileName(String storedFileName);

    void replaceBytes(String storedFileName, byte[] bytes, String contentType);
}
