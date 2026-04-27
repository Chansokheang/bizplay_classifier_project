package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.enums.FileType;
import com.api.bizplay_classifier_api.model.request.FileUploadHistoryRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.DataTrainSummaryResponse;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import com.api.bizplay_classifier_api.repository.FileUploadHistoryRepo;
import com.api.bizplay_classifier_api.service.botConfigService.BotConfigService;
import com.api.bizplay_classifier_api.service.ruleService.RuleService;
import com.api.bizplay_classifier_api.service.storageService.FileStorageService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data")
@AllArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class DataController {
    private final RuleService ruleService;
    private final FileStorageService fileStorageService;
    private final FileUploadHistoryRepo fileUploadHistoryRepo;
    private final BotConfigService botConfigService;

    @PostMapping(value = "/train", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> trainRulesFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "sheetName", required = false) String sheetName,
            @RequestParam(value = "sampleRows", required = false) Integer sampleRows
    ) {
        DataTrainSummaryResponse payload = ruleService.trainRulesFromExcel(file, corpNo, sheetName);

        FileStorageResponse stored = fileStorageService.storeFile(file, FileType.TRAINING);
        fileUploadHistoryRepo.createFileRecord(
                FileUploadHistoryRequest.builder()
                        .companyId(corpNo)
                        .originalFileName(stored.getOriginalFileName())
                        .storedFileName(stored.getStoredFileName())
                        .fileUrl(stored.getFileUrl())
                        .sheetName(sheetName)
                        .fileType(FileType.TRAINING)
                        .build()
        );

        // Auto-generate and persist enhanced prompt after training.
        // sampleRows=null -> use all valid rows.
        botConfigService.updatePromptFromLatestTrainingData(corpNo, sampleRows);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<DataTrainSummaryResponse>builder()
                        .payload(payload)
                        .message("Training completed successfully.")
                        .status(HttpStatus.CREATED)
                        .code(HttpStatus.CREATED.value())
                        .build()
        );
    }
}

