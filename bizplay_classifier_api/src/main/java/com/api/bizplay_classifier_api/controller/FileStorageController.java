package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.FileClassifySummaryDTO;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import com.api.bizplay_classifier_api.model.enums.FileType;
import com.api.bizplay_classifier_api.model.request.FileUploadHistoryRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.FileWithClassifySummaryResponse;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import com.api.bizplay_classifier_api.repository.FileClassifySummaryRepo;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/storage")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class FileStorageController {

    private final FileStorageService fileStorageService;
    private final FileUploadHistoryRepo fileUploadHistoryRepo;
    private final FileClassifySummaryRepo fileClassifySummaryRepo;
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

    @PostMapping(value = "/training-files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadTrainingFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("companyId") UUID companyId
    ) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = fileUploadHistoryRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }

        FileStorageResponse stored = fileStorageService.storeFile(file, FileType.TRAINING);
        FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.createFileRecord(
                FileUploadHistoryRequest.builder()
                        .companyId(companyId)
                        .originalFileName(stored.getOriginalFileName())
                        .storedFileName(stored.getStoredFileName())
                        .fileUrl(stored.getFileUrl())
                        .fileType(FileType.TRAINING)
                        .build()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<FileUploadHistoryDTO>builder()
                        .payload(fileRecord)
                        .fileUrl(stored.getFileUrl())
                        .message("Training file uploaded successfully.")
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
        return getFilesByCompanyId(companyId, null);
    }

    @GetMapping("/files/company/{companyId}/filter")
    public ResponseEntity<ApiResponse<?>> getFilesByCompanyId(
            @PathVariable UUID companyId,
            @RequestParam(value = "fileType", required = false) FileType fileType
    ) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = fileUploadHistoryRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }

        List<FileUploadHistoryDTO> files = fileType == null
                ? fileUploadHistoryRepo.getFilesByCompanyId(companyId)
                : fileUploadHistoryRepo.getFilesByCompanyIdAndFileType(companyId, fileType);
        Map<UUID, FileClassifySummaryDTO> summaryByFileId = fileClassifySummaryRepo.getAllByCompanyId(companyId).stream()
                .collect(Collectors.toMap(
                        FileClassifySummaryDTO::getFileId,
                        s -> s,
                        (existing, ignored) -> existing
                ));
        List<FileWithClassifySummaryResponse> payload = files.stream()
                .map(file -> FileWithClassifySummaryResponse.builder()
                        .file(file)
                        .classifySummary(summaryByFileId.get(file.getFileId()))
                        .build())
                .toList();
        return ResponseEntity.ok(
                ApiResponse.<List<FileWithClassifySummaryResponse>>builder()
                        .payload(payload)
                        .message(fileType == null
                                ? "Files were retrieved successfully."
                                : "Files were retrieved successfully by file type.")
                        .status(HttpStatus.OK)
                        .code(HttpStatus.OK.value())
                        .build()
        );
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<?>> deleteFileById(@PathVariable UUID fileId) {
        FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.getFileById(fileId);
        if (fileRecord == null) {
            throw new CustomNotFoundException("File record not found for id: " + fileId);
        }

        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = fileUploadHistoryRepo.existsCompanyByIdAndUserId(fileRecord.getCompanyId(), currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + fileRecord.getCompanyId());
        }

        fileStorageService.deleteByStoredFileName(fileRecord.getStoredFileName());
        int deletedRows = fileUploadHistoryRepo.deleteFileById(fileId);
        if (deletedRows == 0) {
            throw new CustomNotFoundException("File record not found for id: " + fileId);
        }

        return ResponseEntity.ok(
                ApiResponse.builder()
                        .message("File deleted successfully.")
                        .status(HttpStatus.OK)
                        .code(HttpStatus.OK.value())
                        .build()
        );
    }
}
