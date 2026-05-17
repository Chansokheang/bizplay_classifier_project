package com.api.bizplay_compliance.service.ruleService;

import com.api.bizplay_classifier_api.model.enums.FileType;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import com.api.bizplay_classifier_api.service.storageService.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReceiptFileRegistryService {

    private final FileStorageService fileStorageService;
    private final Map<UUID, ReceiptFileRecord> receiptFiles = new ConcurrentHashMap<>();

    public ReceiptFileRegistryService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public ReceiptFileRecord uploadReceipt(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Receipt file is required.");
        }

        FileStorageResponse storedFile = fileStorageService.storeFile(file, FileType.INPUT);
        ReceiptFileRecord record = new ReceiptFileRecord(
                UUID.randomUUID(),
                storedFile.getOriginalFileName(),
                storedFile.getStoredFileName(),
                storedFile.getFileUrl(),
                storedFile.getContentType()
        );
        receiptFiles.put(record.fileId(), record);
        return record;
    }

    public ReceiptFileContent loadReceipt(UUID fileId) {
        if (fileId == null) {
            throw new IllegalArgumentException("fileId is required.");
        }

        ReceiptFileRecord record = receiptFiles.get(fileId);
        if (record == null) {
            throw new IllegalArgumentException("Receipt file not found for fileId: " + fileId);
        }

        Resource resource = fileStorageService.loadAsResource(record.storedFileName());
        try {
            return new ReceiptFileContent(
                    record.fileId(),
                    record.originalFileName(),
                    record.contentType(),
                    resource.getInputStream().readAllBytes()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read stored receipt file.", exception);
        }
    }

    public record ReceiptFileRecord(
            UUID fileId,
            String originalFileName,
            String storedFileName,
            String fileUrl,
            String contentType
    ) {
    }

    public record ReceiptFileContent(
            UUID fileId,
            String originalFileName,
            String contentType,
            byte[] bytes
    ) {
    }
}
