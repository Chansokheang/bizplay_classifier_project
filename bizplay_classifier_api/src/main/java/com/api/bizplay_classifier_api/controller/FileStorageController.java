package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import com.api.bizplay_classifier_api.repository.FileUploadHistoryRepo;
import com.api.bizplay_classifier_api.service.storageService.FileStorageService;
import com.api.bizplay_classifier_api.utils.GetCurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/storage")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = {"http://localhost:3000"})
public class FileStorageController {

    private final FileStorageService fileStorageService;
    private final FileUploadHistoryRepo fileUploadHistoryRepo;
    private final GetCurrentUser getCurrentUser;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadFile(@RequestPart("file") MultipartFile file) {
        FileStorageResponse payload = fileStorageService.storeFile(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<FileStorageResponse>builder()
                        .payload(payload)
                        .fileUrl(payload.getFileUrl())
                        .message("File uploaded successfully.")
                        .status(HttpStatus.CREATED)
                        .code(HttpStatus.CREATED.value())
                        .build()
        );
    }

    @GetMapping("/files/by-name/{storedFileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String storedFileName) {
        Resource resource = fileStorageService.loadAsResource(storedFileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/files/by-id/{fileId}")
    public ResponseEntity<Resource> downloadFileById(@PathVariable UUID fileId) {
        FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.getFileById(fileId);
        if (fileRecord == null) {
            throw new CustomNotFoundException("File record not found for id: " + fileId);
        }

        Resource resource = fileStorageService.loadAsResource(fileRecord.getStoredFileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileRecord.getOriginalFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/files/company/{companyId}")
    public ResponseEntity<ApiResponse<?>> getFilesByCompanyId(@PathVariable UUID companyId) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = fileUploadHistoryRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }

        List<FileUploadHistoryDTO> payload = fileUploadHistoryRepo.getFilesByCompanyId(companyId);
        return ResponseEntity.ok(
                ApiResponse.<List<FileUploadHistoryDTO>>builder()
                        .payload(payload)
                        .message("Files were retrieved successfully.")
                        .status(HttpStatus.OK)
                        .code(HttpStatus.OK.value())
                        .build()
        );
    }
}
