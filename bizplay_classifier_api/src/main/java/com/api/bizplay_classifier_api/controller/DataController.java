package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.enums.FileType;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import com.api.bizplay_classifier_api.model.request.FileUploadHistoryRequest;
import com.api.bizplay_classifier_api.model.request.TrainingDataTrainRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.DataTrainExecutionResponse;
import com.api.bizplay_classifier_api.model.response.DataTrainSummaryResponse;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import com.api.bizplay_classifier_api.repository.FileUploadHistoryRepo;
import com.api.bizplay_classifier_api.service.botConfigService.BotConfigService;
import com.api.bizplay_classifier_api.service.ruleService.RuleService;
import com.api.bizplay_classifier_api.service.storageService.FileStorageService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    @PostMapping(value = "/train/body", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<?>> trainRulesFromRequestBody(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "corpNo": "1234567890",
                                      "trainingData": [
                                        {
                                          "승인일자": "20251125",
                                          "승인시간": "161309",
                                          "가맹점명": "대한항공직판인터넷",
                                          "가맹점업종코드": "4078",
                                          "가맹점업종명": "인터넷Mall",
                                          "가맹점사업자번호": "1108114794",
                                          "공급금액": 87700,
                                          "부가세액": 0,
                                          "과세유형": "일반",
                                          "용도코드": "A1004",
                                          "용도명": "교통비"
                                        },
                                        {
                                          "승인일자": "20251201",
                                          "승인시간": "175010",
                                          "가맹점명": "(주)여기어때컴퍼니",
                                          "가맹점업종코드": "4076",
                                          "가맹점업종명": "인터넷P/G",
                                          "가맹점사업자번호": "4118601799",
                                          "공급금액": 68000,
                                          "부가세액": 0,
                                          "과세유형": "일반",
                                          "용도코드": "A1005",
                                          "용도명": "출장비"
                                        }
                                      ]
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody TrainingDataTrainRequest trainingDataTrainRequest
    ) {
        DataTrainSummaryResponse payload = ruleService.trainRulesFromRequestData(trainingDataTrainRequest);
        String corpNo = trainingDataTrainRequest.getCorpNo();
        FileStorageResponse stored;
        FileUploadHistoryDTO fileRecord;

        try {
            byte[] storedBytes = createTrainingExcelBytes(trainingDataTrainRequest);
            stored = fileStorageService.storeBytes(
                    storedBytes,
                    "training-data-" + corpNo + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    FileType.TRAINING
            );
            fileRecord = fileUploadHistoryRepo.createFileRecord(
                    FileUploadHistoryRequest.builder()
                            .companyId(corpNo)
                            .originalFileName(stored.getOriginalFileName())
                            .storedFileName(stored.getStoredFileName())
                            .fileUrl(stored.getFileUrl())
                            .sheetName(null)
                            .fileType(FileType.TRAINING)
                            .build()
            );
            botConfigService.updatePromptFromLatestTrainingData(corpNo, null);
        } catch (Exception e) {
            throw new IllegalStateException("Training data was processed, but failed to persist training history.", e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<DataTrainExecutionResponse>builder()
                        .payload(DataTrainExecutionResponse.builder()
                                .summary(payload)
                                .fileId(fileRecord.getFileId())
                                .file(stored)
                                .build())
                        .message("Training completed successfully from request body.")
                        .status(HttpStatus.CREATED)
                        .code(HttpStatus.CREATED.value())
                        .build()
        );
    }

    private byte[] createTrainingExcelBytes(TrainingDataTrainRequest trainingDataTrainRequest) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("training-data");
            createHeaderRow(sheet);

            int rowIndex = 1;
            for (var trainingRow : trainingDataTrainRequest.getTrainingData()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(defaultString(trainingRow.getApprovalDate()));
                row.createCell(1).setCellValue(defaultString(trainingRow.getApprovalTime()));
                row.createCell(2).setCellValue(defaultString(trainingRow.getMerchantName()));
                row.createCell(3).setCellValue(defaultString(trainingRow.getMerchantIndustryCode()));
                row.createCell(4).setCellValue(defaultString(trainingRow.getMerchantIndustryName()));
                row.createCell(5).setCellValue(defaultString(trainingRow.getMerchantBusinessNumber()));
                row.createCell(6).setCellValue(trainingRow.getSupplyAmount() == null ? 0 : trainingRow.getSupplyAmount());
                row.createCell(7).setCellValue(trainingRow.getVatAmount() == null ? 0 : trainingRow.getVatAmount());
                row.createCell(8).setCellValue(defaultString(trainingRow.getTaxType()));
                row.createCell(9).setCellValue(defaultString(trainingRow.getCategoryCode()));
                row.createCell(10).setCellValue(defaultString(trainingRow.getCategoryName()));
            }

            for (int i = 0; i <= 10; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate training Excel file.", e);
        }
    }

    private void createHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("승인일자");
        header.createCell(1).setCellValue("승인시간");
        header.createCell(2).setCellValue("가맹점명");
        header.createCell(3).setCellValue("가맹점업종코드");
        header.createCell(4).setCellValue("가맹점업종명");
        header.createCell(5).setCellValue("가맹점사업자번호");
        header.createCell(6).setCellValue("공급금액");
        header.createCell(7).setCellValue("부가세액");
        header.createCell(8).setCellValue("과세유형");
        header.createCell(9).setCellValue("용도코드");
        header.createCell(10).setCellValue("용도명");
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

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

