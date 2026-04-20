package com.api.bizplay_classifier_api.service.transactionService;

import com.api.bizplay_classifier_api.model.dto.FileClassifySummaryDTO;
import com.api.bizplay_classifier_api.model.request.FileRowPatchRequest;
import com.api.bizplay_classifier_api.model.request.TransactionRequest;
import com.api.bizplay_classifier_api.model.response.FileRowPatchResponse;
import com.api.bizplay_classifier_api.model.response.FileTransactionsPageResponse;
import com.api.bizplay_classifier_api.model.response.TransactionResponse;
import com.api.bizplay_classifier_api.model.response.TransactionUploadSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface TransactionService {
    TransactionResponse createTransaction(TransactionRequest transactionRequest);

    TransactionUploadSummaryResponse createSingleTransactionForTesting(TransactionRequest transactionRequest);

    TransactionUploadSummaryResponse createTransactionsByExcel(MultipartFile file, UUID defaultCompanyId, String sheetName);

    TransactionUploadSummaryResponse createTransactionsByExcel(byte[] fileBytes, UUID defaultCompanyId, String sheetName);

    /**
     * Apply manual row-level corrections to an already-enriched Excel file stored in MinIO.
     * For each update: sets 용도코드, resolves 용도명 from the category master, sets 방법="Updated",
     * and clears the Reason column.
     */
    FileRowPatchResponse patchFileRows(UUID fileId, FileRowPatchRequest request);

    java.util.List<FileClassifySummaryDTO> getAllFileClassifySummariesByCompanyId(UUID companyId);

    FileTransactionsPageResponse getTransactionsByFileId(UUID fileId, int page, int limit);
}
