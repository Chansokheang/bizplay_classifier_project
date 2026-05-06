package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.FileClassifySummaryDTO;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import com.api.bizplay_classifier_api.model.request.FileRowPatchRequest;
import com.api.bizplay_classifier_api.model.request.SingleTransactionTestRequest;
import com.api.bizplay_classifier_api.model.request.TransactionRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.FileTransactionsPageResponse;
import com.api.bizplay_classifier_api.model.response.FileRowPatchResponse;
import com.api.bizplay_classifier_api.model.response.SingleTransactionTestResponse;
import com.api.bizplay_classifier_api.model.response.TransactionResponse;
import com.api.bizplay_classifier_api.model.response.TransactionUploadSummaryResponse;
import com.api.bizplay_classifier_api.repository.FileUploadHistoryRepo;
import com.api.bizplay_classifier_api.service.transactionService.TransactionService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@AllArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class TransactionController {

    private final TransactionService transactionService;
    private final FileUploadHistoryRepo fileUploadHistoryRepo;

    @PostMapping("/test-single-transaction/create")
    public ResponseEntity<ApiResponse<?>> createSingleTransactionForTesting(
            @RequestParam("corpNo") String corpNo,
            @Valid @RequestBody SingleTransactionTestRequest request
    ) {
        SingleTransactionTestResponse payload =
                transactionService.createSingleTransactionForTesting(
                        corpNo,
                        TransactionRequest.builder()
                                .companyId(corpNo)
                                .approvalDate(request.getApprovalDate())
                                .approvalTime(request.getApprovalTime())
                                .merchantName(request.getMerchantName())
                                .merchantIndustryCode(request.getMerchantIndustryCode())
                                .merchantIndustryName(request.getMerchantIndustryName())
                                .merchantBusinessRegistrationNumber(request.getMerchantBusinessRegistrationNumber())
                                .supplyAmount(request.getSupplyAmount())
                                .vatAmount(request.getVatAmount())
                                .taxType(request.getTaxType())
                                .build()
                );
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SingleTransactionTestResponse>builder()
                        .payload(payload)
                        .message("Single transaction was processed successfully.")
                        .code(HttpStatus.CREATED.value())
                        .status(HttpStatus.CREATED)
                        .build()
        );
    }

    @PutMapping("/files/{fileId}/rows")
    public ResponseEntity<ApiResponse<?>> patchFileRows(
            @PathVariable UUID fileId,
            @Valid @RequestBody FileRowPatchRequest request
    ) {
        FileRowPatchResponse payload = transactionService.patchFileRows(fileId, request);
        return ResponseEntity.ok(
                ApiResponse.<FileRowPatchResponse>builder()
                        .payload(payload)
                        .message("File rows updated successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadTransactionsByExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "corpNo", required = false) String corpNo,
            @RequestParam(value = "sheetName", required = false) String sheetName
    ) {
        TransactionUploadSummaryResponse payload = transactionService.createTransactionsByExcel(file, corpNo, sheetName);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TransactionUploadSummaryResponse>builder()
                        .payload(payload)
                        .message("Transactions were created successfully from Excel.")
                        .code(HttpStatus.CREATED.value())
                        .status(HttpStatus.CREATED)
                        .build()
        );
    }

    @GetMapping("/files/corp/{corpNo}/classify-summaries")
    public ResponseEntity<ApiResponse<?>> getAllClassifySummariesByCorpNo(@PathVariable String corpNo) {
        List<FileClassifySummaryDTO> payload = transactionService.getAllFileClassifySummariesByCompanyId(corpNo);
        return ResponseEntity.ok(
                ApiResponse.<List<FileClassifySummaryDTO>>builder()
                        .payload(payload)
                        .message("File classify summaries were retrieved successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/files/{id}/transactions")
    public ResponseEntity<ApiResponse<?>> getFileTransactions(
            @PathVariable("id") UUID fileId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.getFileById(fileId);
        if (fileRecord == null) {
            throw new CustomNotFoundException("File record not found for id: " + fileId);
        }

        if (fileRecord.getCompanyId() != null) {
            int exists = fileUploadHistoryRepo.existsCompanyById(fileRecord.getCompanyId());
            if (exists == 0) {
                throw new CustomNotFoundException("Corp was not found with corpNo: " + fileRecord.getCompanyId());
            }
        }

        FileTransactionsPageResponse payload = transactionService.getTransactionsByFileId(fileId, page, limit);
        return ResponseEntity.ok(
                ApiResponse.<FileTransactionsPageResponse>builder()
                        .payload(payload)
                        .message("File transactions were retrieved successfully.")
                        .status(HttpStatus.OK)
                        .code(HttpStatus.OK.value())
                        .build()
        );
    }

//    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
//    public ResponseEntity<ApiResponse<?>> uploadTransactionsByExcelRaw(
//            @RequestBody byte[] fileBytes,
//            @RequestParam(value = "companyId", required = false) String companyId,
//            @RequestParam(value = "sheetName", required = false) String sheetName
//    ) {
//        TransactionUploadSummaryResponse payload = transactionService.createTransactionsByExcel(fileBytes, companyId, sheetName);
//        return ResponseEntity.status(HttpStatus.CREATED).body(
//                ApiResponse.<TransactionUploadSummaryResponse>builder()
//                        .payload(payload)
//                        .message("Transactions were created successfully from Excel.")
//                        .code(HttpStatus.CREATED.value())
//                        .status(HttpStatus.CREATED)
//                        .build()
//        );
//    }
}

