package com.api.bizplay_classifier_api.service.transactionService;

import com.api.bizplay_classifier_api.model.request.TransactionRequest;
import com.api.bizplay_classifier_api.model.response.TransactionResponse;
import com.api.bizplay_classifier_api.model.response.TransactionUploadSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface TransactionService {
    TransactionResponse createTransaction(TransactionRequest transactionRequest);

    TransactionUploadSummaryResponse createTransactionsByExcel(MultipartFile file, UUID defaultCompanyId, String sheetName);

    TransactionUploadSummaryResponse createTransactionsByExcel(byte[] fileBytes, UUID defaultCompanyId, String sheetName);
}
