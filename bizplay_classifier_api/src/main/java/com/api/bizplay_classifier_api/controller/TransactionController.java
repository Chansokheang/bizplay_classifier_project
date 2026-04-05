package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.request.TransactionRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.TransactionResponse;
import com.api.bizplay_classifier_api.model.response.TransactionUploadSummaryResponse;
import com.api.bizplay_classifier_api.service.transactionService.TransactionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = {"http://localhost:3000"})
public class TransactionController {

    private final TransactionService transactionService;

//    @PostMapping("/create")
//    public ResponseEntity<ApiResponse<?>> createTransaction(@Valid @RequestBody TransactionRequest transactionRequest) {
//        return ResponseEntity.status(HttpStatus.CREATED).body(
//                ApiResponse.<TransactionResponse>builder()
//                        .payload(transactionService.createTransaction(transactionRequest))
//                        .message("Transaction was created successfully.")
//                        .code(HttpStatus.CREATED.value())
//                        .status(HttpStatus.CREATED)
//                        .build()
//        );
//    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadTransactionsByExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "companyId", required = false) UUID companyId,
            @RequestParam(value = "sheetName", required = false) String sheetName
    ) {
        TransactionUploadSummaryResponse payload = transactionService.createTransactionsByExcel(file, companyId, sheetName);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TransactionUploadSummaryResponse>builder()
                        .payload(payload)
                        .message("Transactions were created successfully from Excel.")
                        .code(HttpStatus.CREATED.value())
                        .status(HttpStatus.CREATED)
                        .build()
        );
    }

//    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
//    public ResponseEntity<ApiResponse<?>> uploadTransactionsByExcelRaw(
//            @RequestBody byte[] fileBytes,
//            @RequestParam(value = "companyId", required = false) UUID companyId,
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
