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
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/storage")
@AllArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class FileStorageController {

    private final FileStorageService fileStorageService;
    private final FileUploadHistoryRepo fileUploadHistoryRepo;
    private final FileClassifySummaryRepo fileClassifySummaryRepo;

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
            @RequestParam("corpNo") String corpNo
    ) {
        int exists = fileUploadHistoryRepo.existsCompanyById(corpNo);
        if (exists == 0) {
            throw new CustomNotFoundException("Corp was not found with corpNo: " + corpNo);
        }

        FileStorageResponse stored = fileStorageService.storeFile(file, FileType.TRAINING);
        FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.createFileRecord(
                FileUploadHistoryRequest.builder()
                        .companyId(corpNo)
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
        String downloadFileName = extractFileName(resource.getFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentDisposition(downloadFileName))
                .contentType(resolveMediaType(downloadFileName))
                .body(resource);
    }

    @GetMapping("/files/by-id/{fileId}")
    public ResponseEntity<Resource> downloadFileById(@PathVariable UUID fileId) {
        FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.getFileById(fileId);
        if (fileRecord == null) {
            throw new CustomNotFoundException("File record not found for id: " + fileId);
        }

        Resource resource = fileStorageService.loadAsResource(fileRecord.getStoredFileName());
        String downloadFileName = extractFileName(fileRecord.getOriginalFileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentDisposition(downloadFileName))
                .contentType(resolveMediaType(downloadFileName))
                .body(resource);
    }

    private MediaType resolveMediaType(String fileName) {
        return MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private String buildAttachmentDisposition(String fileName) {
        return ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private String extractFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "download";
        }
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return lastSlash >= 0 ? fileName.substring(lastSlash + 1) : fileName;
    }

    @GetMapping("/files/corp/{corpNo}")
    public ResponseEntity<ApiResponse<?>> getFilesByCorpNo(@PathVariable String corpNo) {
        return getFilesByCorpNo(corpNo, null);
    }

    @GetMapping("/files/corp/{corpNo}/filter")
    public ResponseEntity<ApiResponse<?>> getFilesByCorpNo(
            @PathVariable String corpNo,
            @RequestParam(value = "fileType", required = false) FileType fileType
    ) {
        int exists = fileUploadHistoryRepo.existsCompanyById(corpNo);
        if (exists == 0) {
            throw new CustomNotFoundException("Corp was not found with corpNo: " + corpNo);
        }

        List<FileUploadHistoryDTO> files = fileType == null
                ? fileUploadHistoryRepo.getFilesByCompanyId(corpNo)
                : fileUploadHistoryRepo.getFilesByCompanyIdAndFileType(corpNo, fileType);
        Map<UUID, FileClassifySummaryDTO> summaryByFileId = fileClassifySummaryRepo.getAllByCompanyId(corpNo).stream()
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

        int exists = fileUploadHistoryRepo.existsCompanyById(fileRecord.getCompanyId());
        if (exists == 0) {
            throw new CustomNotFoundException("Corp was not found with corpNo: " + fileRecord.getCompanyId());
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

