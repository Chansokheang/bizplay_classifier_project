package com.api.bizplay_classifier_api.service.transactionService;

import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;
import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.dto.FileClassifySummaryDTO;
import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.request.FileRowPatchRequest;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.TransactionRequest;
import com.api.bizplay_classifier_api.model.request.FileUploadHistoryRequest;
import com.api.bizplay_classifier_api.model.response.FileRowPatchResponse;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import com.api.bizplay_classifier_api.model.response.FileTransactionsPageResponse;
import com.api.bizplay_classifier_api.model.response.TransactionResponse;
import com.api.bizplay_classifier_api.model.response.TransactionUploadSummaryResponse;
import com.api.bizplay_classifier_api.repository.CategoryRepo;
import com.api.bizplay_classifier_api.repository.FileUploadHistoryRepo;
import com.api.bizplay_classifier_api.repository.BotConfigRepo;
import com.api.bizplay_classifier_api.repository.FileClassifySummaryRepo;
import com.api.bizplay_classifier_api.repository.RuleRepo;
import com.api.bizplay_classifier_api.service.aiFallbackService.AiFallbackService;
import com.api.bizplay_classifier_api.service.botConfigService.BotConfigDefaults;
import com.api.bizplay_classifier_api.service.companyService.CompanyService;
import com.api.bizplay_classifier_api.service.storageService.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TransactionServiceImple implements TransactionService {

    private final FileUploadHistoryRepo fileUploadHistoryRepo;
    private final RuleRepo ruleRepo;
    private final BotConfigRepo botConfigRepo;
    private final FileClassifySummaryRepo fileClassifySummaryRepo;
    private final CategoryRepo categoryRepo;
    private final CompanyService companyService;
    private final FileStorageService fileStorageService;
    private final AiFallbackService aiFallbackService;
    private final ObjectMapper objectMapper;
    private static final int EXCEL_BATCH_SIZE = 500;
    private static final int AI_FALLBACK_CONTEXT_LIMIT = 30;
    private static final String USAGE_CODE_HEADER = "field_name1";
    private static final String USAGE_NAME_HEADER = "usage_name";
    private static final String METHOD_HEADER = "method";
    private static final String DESCRIPTION_HEADER = "description";

    private static final List<String> REQUIRED_HEADERS = List.of(
            "approval_date",
            "approval_time",
            "merchant_name",
            "merchant_industry_code"
    );

    private static final List<String> ALL_HEADERS = List.of(
            "company_id",
            "approval_date",
            "approval_time",
            "merchant_name",
            "merchant_industry_code",
            "merchant_industry_name",
            "merchant_business_registration_number",
            "supply_amount",
            "vat_amount",
            "tax_type",
            "field_name1",
            "usage_name",
            "pk",
            "user_tx_id",
            "writer_tx_id",
            "hoi",
            "method",
            "description"
    );

    private static final Map<String, String> DISPLAY_HEADERS = Map.ofEntries(
            Map.entry("hoi", "HOI"),
            Map.entry("pk", "pk"),
            Map.entry("approval_date", "승인일자"),
            Map.entry("approval_time", "승인시간"),
            Map.entry("merchant_name", "가맹점명"),
            Map.entry("merchant_industry_code", "가맹점업종코드"),
            Map.entry("merchant_industry_name", "가맹점업종명"),
            Map.entry("merchant_business_registration_number", "가맹점사업자번호"),
            Map.entry("supply_amount", "공급금액"),
            Map.entry("vat_amount", "부가세액"),
            Map.entry("tax_type", "과세유형"),
            Map.entry("user_tx_id", "사용자id"),
            Map.entry("writer_tx_id", "작성자id"),
            Map.entry("field_name1", "용도코드"),
            Map.entry("usage_name", "용도명"),
            Map.entry("method", "방법"),
            Map.entry("description", "Reason")
    );

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("companyid", "company_id"),
            Map.entry("이용기관id", "company_id"),
            Map.entry("approvaldate", "approval_date"),
            Map.entry("승인일자", "approval_date"),
            Map.entry("수입일자", "approval_date"),
            Map.entry("approvaltime", "approval_time"),
            Map.entry("승인시간", "approval_time"),
            Map.entry("merchantname", "merchant_name"),
            Map.entry("가맹점명", "merchant_name"),
            Map.entry("merchantindustrycode", "merchant_industry_code"),
            Map.entry("가맹점업종코드", "merchant_industry_code"),
            Map.entry("merchantindustryname", "merchant_industry_name"),
            Map.entry("가맹점업종명", "merchant_industry_name"),
            Map.entry("merchantbusinessregistrationnumber", "merchant_business_registration_number"),
            Map.entry("가맹점사업자번호", "merchant_business_registration_number"),
            Map.entry("가맹점사업자등록번호", "merchant_business_registration_number"),
            Map.entry("supplyamount", "supply_amount"),
            Map.entry("공급금액", "supply_amount"),
            Map.entry("공급가액", "supply_amount"),
            Map.entry("vatamount", "vat_amount"),
            Map.entry("부가세액", "vat_amount"),
            Map.entry("taxtype", "tax_type"),
            Map.entry("과세유형", "tax_type"),
            Map.entry("거래구분", "tax_type"),
            Map.entry("fieldname1", "field_name1"),
            Map.entry("용도코드", "field_name1"),
            Map.entry("용도명", "usage_name"),
            Map.entry("입력항목명", "usage_name"),
            Map.entry("pk", "pk"),
            Map.entry("usertxid", "user_tx_id"),
            Map.entry("사용자id", "user_tx_id"),
            Map.entry("writertxid", "writer_tx_id"),
            Map.entry("작성자id", "writer_tx_id")
    );

    @Override
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest transactionRequest) {
        throw new IllegalArgumentException("Transaction table persistence is disabled. Use /api/v1/transactions/upload for file processing only.");
    }

    @Override
    @Transactional
    public TransactionUploadSummaryResponse createTransactionsByExcel(MultipartFile file, UUID defaultCompanyId, String sheetName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required.");
        }
        try {
            byte[] fileBytes = file.getBytes();
            String originalFileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "transactions_input.xlsx"
                    : file.getOriginalFilename();
            return createTransactionsByExcel(fileBytes, defaultCompanyId, sheetName, originalFileName);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read Excel file.", e);
        }
    }

    @Override
    @Transactional
    public TransactionUploadSummaryResponse createTransactionsByExcel(byte[] fileBytes, UUID defaultCompanyId, String sheetName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("Excel file bytes are required.");
        }
        try {
            return createTransactionsByExcel(fileBytes, defaultCompanyId, sheetName, "transactions_input.xlsx");
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read Excel file bytes.", e);
        }
    }

    private TransactionUploadSummaryResponse createTransactionsByExcel(byte[] fileBytes, UUID defaultCompanyId, String sheetName, String originalFileName) throws IOException {
        if (defaultCompanyId != null) {
            companyService.getCompanyByCompanyId(defaultCompanyId);
        }

        FileStorageResponse inputFile = fileStorageService.storeBytes(
                fileBytes,
                originalFileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                com.api.bizplay_classifier_api.model.enums.FileType.INPUT
        );
        fileUploadHistoryRepo.createFileRecord(
                FileUploadHistoryRequest.builder()
                        .companyId(defaultCompanyId)
                        .originalFileName(inputFile.getOriginalFileName())
                        .storedFileName(inputFile.getStoredFileName())
                        .fileUrl(inputFile.getFileUrl())
                        .sheetName(sheetName)
                        .fileType(com.api.bizplay_classifier_api.model.enums.FileType.INPUT)
                        .build()
        );

        try (InputStream inputStream = new ByteArrayInputStream(fileBytes);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet;
            if (sheetName != null && !sheetName.isBlank()) {
                sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    throw new IllegalArgumentException("Sheet not found: " + sheetName);
                }
            } else {
                sheet = workbook.getSheetAt(0);
            }

            if (sheet == null || sheet.getPhysicalNumberOfRows() < 2) {
                throw new IllegalArgumentException("Excel must contain a header row and at least one data row.");
            }

            DataFormatter formatter = new DataFormatter();
            HeaderParseResult headerParseResult = findHeaderMap(sheet, formatter);
            Map<String, Integer> headerMap = headerParseResult.headerMap();

            validateRequiredHeaders(headerMap, defaultCompanyId);
            ensureUsageAndMethodColumnsSafe(sheet.getRow(headerParseResult.headerRowIndex()), headerMap);

            Map<UUID, List<RuleClassifierDTO>> rulesCache = new HashMap<>();
            Map<UUID, BotConfigRequest.Config> botConfigCache = new HashMap<>();
            Map<String, AiFallbackService.AiFallbackResult> aiResultCache = new HashMap<>();
            Set<String> usedRuleKeys = new HashSet<>();
            UUID fileCompanyId = defaultCompanyId;
            int totalRows = 0;
            int skippedRows = 0;
            int insertedRows = 0;
            int ruleMatchedRows = 0;
            int aiMatchedRows = 0;
            int ruleUnmatchedRows = 0;
            int processedSingleLabelRows = 0;
            List<String> unmatchedMerchantSamples = new ArrayList<>();

            for (int rowIndex = headerParseResult.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                totalRows++;
                if (isRowEmpty(row, formatter)) {
                    skippedRows++;
                    continue;
                }

                UUID companyId = resolveCompanyId(row, headerMap, formatter, rowIndex, defaultCompanyId);
                fileCompanyId = mergeFileCompanyId(fileCompanyId, companyId);
                UsageValue usageValue = resolveUsageValueByMerchantName(
                        row,
                        headerMap,
                        formatter,
                        companyId,
                        rulesCache,
                        usedRuleKeys
                );
                boolean disambiguateMultiCode = hasMultiLabel(usageValue.usageCode());
                boolean shouldCallAi = !usageValue.matchedByRule()
                        || usageValue.usageCode() == null || usageValue.usageCode().isBlank()
                        || usageValue.usageName() == null || usageValue.usageName().isBlank()
                        || disambiguateMultiCode;
                if (shouldCallAi) {
                    UsageValue aiUsageValue = resolveUsageValueByAi(
                            row,
                            headerMap,
                            formatter,
                            companyId,
                            rulesCache,
                            botConfigCache,
                            aiResultCache,
                            usageValue,
                            disambiguateMultiCode
                    );
                    if (aiUsageValue != null) {
                        usageValue = aiUsageValue;
                    }
                }
                applyUsageValueToSheet(row, headerMap, usageValue);
                upsertRuleCategoryFromRow(
                        companyId,
                        getCellValue(row, headerMap, formatter, "merchant_industry_code"),
                        getCellValue(row, headerMap, formatter, "merchant_industry_name"),
                        usageValue.usageCode(),
                        usageValue.usageName()
                );
                if (usageValue.matchedByAi() && !usageValue.matchedByRule()) {
                    // Pure AI fallback (rule did not match)
                    aiMatchedRows++;
                } else if (usageValue.matchedByRule()) {
                    // Rule-based match (includes Rule+AI disambiguation)
                    ruleMatchedRows++;
                } else {
                    ruleUnmatchedRows++;
                    if (unmatchedMerchantSamples.size() < 10) {
                        String merchant = getCellValue(row, headerMap, formatter, "merchant_name");
                        if (merchant != null && !merchant.isBlank()) {
                            unmatchedMerchantSamples.add(merchant);
                        }
                    }
                }

                // processed_rows should count only single-label outputs (exclude multi-label codes like A1003,A1007)
                if (usageValue.usageCode() != null
                        && !usageValue.usageCode().isBlank()
                        && !hasMultiLabel(usageValue.usageCode())) {
                    processedSingleLabelRows++;
                }

                // Transaction table is not used anymore.
                // Keep enrichment + file output only.
            }

            FileStorageResponse enrichedFile = storeEnrichedWorkbook(workbook, sheetName, originalFileName);
            com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.createFileRecord(
                    FileUploadHistoryRequest.builder()
                            .companyId(fileCompanyId)
                            .originalFileName(originalFileName)
                            .storedFileName(enrichedFile.getStoredFileName())
                            .fileUrl(enrichedFile.getFileUrl())
                            .sheetName(sheetName)
                            .fileType(com.api.bizplay_classifier_api.model.enums.FileType.OUTPUT)
                            .build()
            );

            if (fileCompanyId == null) {
                throw new IllegalArgumentException("Unable to determine companyId for file classify summary.");
            }
            FileClassifySummaryDTO savedSummary = fileClassifySummaryRepo.createSummary(
                    fileRecord.getFileId(),
                    fileCompanyId,
                    totalRows,
                    processedSingleLabelRows,
                    skippedRows,
                    ruleMatchedRows,
                    aiMatchedRows,
                    ruleUnmatchedRows
            );

            return TransactionUploadSummaryResponse.builder()
                    .fileId(fileRecord.getFileId())
                    .totalRows(totalRows)
                    .insertedRows(insertedRows)
                    .skippedRows(skippedRows)
                    .batchSize(EXCEL_BATCH_SIZE)
                    .enrichedFileUrl(enrichedFile.getFileUrl())
                    .storedFileName(enrichedFile.getStoredFileName())
                    .ruleMatchedRows(ruleMatchedRows)
                    .aiMatchedRows(aiMatchedRows)
                    .ruleUnmatchedRows(ruleUnmatchedRows)
                    .unmatchedMerchantSamples(unmatchedMerchantSamples)
                    .fileClassifySummary(savedSummary)
                    .build();
        }
    }

    @Override
    public List<FileClassifySummaryDTO> getAllFileClassifySummariesByCompanyId(UUID companyId) {
        companyService.getCompanyByCompanyId(companyId);
        return fileClassifySummaryRepo.getAllByCompanyId(companyId);
    }

    @Override
    public FileTransactionsPageResponse getTransactionsByFileId(UUID fileId, int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1.");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000.");
        }

        com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.getFileById(fileId);
        if (fileRecord == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        try (InputStream inputStream = fileStorageService.loadAsResource(fileRecord.getStoredFileName()).getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook, fileRecord.getSheetName());
            DataFormatter formatter = new DataFormatter();
            HeaderParseResult header = findHeaderMap(sheet, formatter);
            Map<String, Integer> headerMap = header.headerMap();

            List<Map<String, String>> allRows = new ArrayList<>();
            List<Map.Entry<String, Integer>> orderedHeaders = headerMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .toList();
            int dataRowCounter = 0;
            for (int rowIndex = header.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isRowEmpty(row, formatter)) {
                    continue;
                }
                dataRowCounter++;
                Map<String, String> rowData = new LinkedHashMap<>();
                rowData.put("row_index", String.valueOf(dataRowCounter));
                for (Map.Entry<String, Integer> h : orderedHeaders) {
                    String value = getCellValue(row, headerMap, formatter, h.getKey());
                    String displayKey = DISPLAY_HEADERS.getOrDefault(h.getKey(), h.getKey());
                    rowData.put(displayKey, value == null ? "" : value);
                }
                allRows.add(rowData);
            }

            int totalRows = allRows.size();
            int totalPages = totalRows == 0 ? 0 : (int) Math.ceil((double) totalRows / limit);
            int from = (page - 1) * limit;
            if (from >= totalRows) {
                return FileTransactionsPageResponse.builder()
                        .fileId(fileId)
                        .page(page)
                        .limit(limit)
                        .totalRows(totalRows)
                        .totalPages(totalPages)
                        .items(List.of())
                        .build();
            }
            int to = Math.min(from + limit, totalRows);
            List<Map<String, String>> items = allRows.subList(from, to);

            return FileTransactionsPageResponse.builder()
                    .fileId(fileId)
                    .page(page)
                    .limit(limit)
                    .totalRows(totalRows)
                    .totalPages(totalPages)
                    .items(items)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read transactions from file: " + fileId, e);
        }
    }

    private UUID mergeFileCompanyId(UUID current, UUID candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        if (current.equals(candidate)) {
            return current;
        }
        // mixed-company file
        return null;
    }

    private HeaderParseResult findHeaderMap(Sheet sheet, DataFormatter formatter) {
        int lastRowToScan = Math.min(sheet.getLastRowNum(), 20);
        Map<String, Integer> bestHeaderMap = new HashMap<>();
        int bestHeaderRowIndex = -1;

        for (int rowIndex = 0; rowIndex <= lastRowToScan; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, Integer> currentMap = parseHeaderMap(row, formatter);
            if (currentMap.size() > bestHeaderMap.size()) {
                bestHeaderMap = currentMap;
                bestHeaderRowIndex = rowIndex;
            }
        }

        if (bestHeaderRowIndex < 0 || bestHeaderMap.isEmpty()) {
            throw new IllegalArgumentException(
                    "Wrong file header format. Required headers: "
                            + "승인일자, 승인시간, 가맹점명, 가맹점업종코드"
                            + ". Detected mapped headers: []"
            );
        }

        return new HeaderParseResult(bestHeaderRowIndex, bestHeaderMap);
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName) {
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet named = workbook.getSheet(sheetName);
            if (named != null) {
                return named;
            }
        }
        Sheet first = workbook.getSheetAt(0);
        if (first == null) {
            throw new IllegalArgumentException("Sheet not found in uploaded file.");
        }
        return first;
    }

    private Map<String, Integer> parseHeaderMap(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> headerMap = new HashMap<>();
        if (headerRow == null || headerRow.getLastCellNum() < 0) {
            return headerMap;
        }

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String rawHeader = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (rawHeader.isEmpty()) {
                continue;
            }
            String canonicalHeader = resolveHeaderSafe(rawHeader);
            if (canonicalHeader != null) {
                headerMap.put(canonicalHeader, i);
            }
        }
        return headerMap;
    }

    private String resolveHeaderSafe(String rawHeader) {
        String normalized = normalizeHeader(rawHeader);

        if (normalized.equals("hoi")) {
            return "hoi";
        }
        if (containsAny(normalized, "가맹점명")) {
            return "merchant_name";
        }
        if (containsAny(normalized, "가맹점업종코드")) {
            return "merchant_industry_code";
        }
        if (containsAny(normalized, "가맹점업종명")) {
            return "merchant_industry_name";
        }
        if (containsAny(normalized, "가맹점사업자번호", "사업자번호")) {
            return "merchant_business_registration_number";
        }
        if (containsAny(normalized, "용도코드")) {
            return "field_name1";
        }
        if (containsAny(normalized, "용도명", "입력항목명")) {
            return "usage_name";
        }
        if (containsAny(normalized, "사용자id")) {
            return "user_tx_id";
        }
        if (containsAny(normalized, "작성자id")) {
            return "writer_tx_id";
        }
        return resolveHeader(rawHeader);
    }

    private String resolveHeader(String rawHeader) {
        String normalized = normalizeHeader(rawHeader);

        String mapped = HEADER_ALIASES.get(normalized);
        if (mapped != null) {
            return mapped;
        }

        if (containsAny(normalized, "method", "방법")) {
            return METHOD_HEADER;
        }
        if (containsAny(normalized, "reason", "description", "사유")) {
            return DESCRIPTION_HEADER;
        }

        if (containsAny(normalized, "approvaldate", "승인일자", "수입일자")) {
            return "approval_date";
        }
        if (containsAny(normalized, "approvaltime", "승인시간")) {
            return "approval_time";
        }
        if (containsAny(normalized, "merchantindustrycode", "가맹점업종코드")) {
            return "merchant_industry_code";
        }
        if (containsAny(normalized, "merchantindustryname", "가맹점업종명")) {
            return "merchant_industry_name";
        }
        if (containsAny(normalized, "merchantbusinessregistrationnumber", "businessregistrationnumber",
                "가맹점사업자번호", "가맹점사업자등록번호")) {
            return "merchant_business_registration_number";
        }
        if (containsAny(normalized, "merchantname", "가맹점명")) {
            return "merchant_name";
        }
        if (containsAny(normalized, "supplyamount", "공급금액", "공급가액")) {
            return "supply_amount";
        }
        if (containsAny(normalized, "vatamount", "부가세액")) {
            return "vat_amount";
        }
        if (containsAny(normalized, "taxtype", "과세유형", "거래구분")) {
            return "tax_type";
        }
        if (containsAny(normalized, "fieldname1", "용도코드")) {
            return "field_name1";
        }
        if (containsAny(normalized, "usagename", "용도명", "입력항목명")) {
            return "usage_name";
        }
        if (containsAny(normalized, "usertxid", "사용자id")) {
            return "user_tx_id";
        }
        if (containsAny(normalized, "writertxid", "작성자id")) {
            return "writer_tx_id";
        }
        if (normalized.equals("pk")) {
            return "pk";
        }
        if (containsAny(normalized, "companyid", "이용기관id")) {
            return "company_id";
        }
        return null;
    }

    private String normalizeHeader(String rawHeader) {
        return rawHeader
                .replace("\uFEFF", "")
                .replaceAll("[^0-9a-zA-Z\\uAC00-\\uD7A3]+", "")
                .toLowerCase();
    }

    private boolean containsAny(String normalized, String... tokens) {
        for (String token : tokens) {
            if (normalized.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerMap, UUID defaultCompanyId) {
        List<String> missing = new ArrayList<>();
        if (!headerMap.containsKey("approval_date")) missing.add("승인일자");
        if (!headerMap.containsKey("approval_time")) missing.add("승인시간");
        if (!headerMap.containsKey("merchant_name")) missing.add("가맹점명");
        if (!headerMap.containsKey("merchant_industry_code")) missing.add("가맹점업종코드");
        if (defaultCompanyId == null && !headerMap.containsKey("company_id")) {
            missing.add("이용기관ID(company_id) 또는 companyId 파라미터");
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Wrong file header format. Required headers: "
                            + "승인일자, 승인시간, 가맹점명, 가맹점업종코드"
                            + (defaultCompanyId == null ? ", 이용기관ID(company_id)" : "")
                            + ". Missing: " + String.join(", ", missing)
                            + ". Detected mapped headers: " + headerMap.keySet()
            );
        }
    }

    private void ensureUsageColumns(Row headerRow, Map<String, Integer> headerMap) {
        if (headerRow == null) {
            return;
        }
        short nextCol = headerRow.getLastCellNum() < 0 ? 0 : headerRow.getLastCellNum();
        if (!headerMap.containsKey(USAGE_CODE_HEADER)) {
            headerMap.put(USAGE_CODE_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("용도코드");
            nextCol++;
        }
        if (!headerMap.containsKey(USAGE_NAME_HEADER)) {
            headerMap.put(USAGE_NAME_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("용도명");
        }
    }

    private UUID resolveCompanyId(Row row, Map<String, Integer> headerMap, DataFormatter formatter, int rowIndex, UUID defaultCompanyId) {
        UUID companyId = defaultCompanyId;
        if (companyId == null) {
            String companyIdRaw = getCellValue(row, headerMap, formatter, "company_id");
            if (companyIdRaw != null && !companyIdRaw.isBlank()) {
                try {
                    companyId = UUID.fromString(companyIdRaw);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Row " + (rowIndex + 1) + ": company_id must be a valid UUID.");
                }
            }
        }
        if (companyId == null) {
            throw new IllegalArgumentException("Row " + (rowIndex + 1) + ": company_id is required.");
        }
        return companyId;
    }

    private UsageValue resolveUsageValueByMerchantName(Row row, Map<String, Integer> headerMap, DataFormatter formatter, UUID companyId,
                                                       Map<UUID, List<RuleClassifierDTO>> rulesCache,
                                                       Set<String> usedRuleKeys) {
        String usageCode = getCellValue(row, headerMap, formatter, USAGE_CODE_HEADER);
        String usageName = getCellValue(row, headerMap, formatter, USAGE_NAME_HEADER);
        String merchantIndustryCode = getCellValue(row, headerMap, formatter, "merchant_industry_code");
        if (merchantIndustryCode == null || merchantIndustryCode.isBlank()) {
            return UsageValue.unmatched(usageCode, usageName);
        }

        List<RuleClassifierDTO> classifiers = rulesCache.computeIfAbsent(
                companyId, ruleRepo::getRuleClassifiersByCompanyId
        );
        if (classifiers == null || classifiers.isEmpty()) {
            throw new IllegalArgumentException(
                    "No rules configured for company: " + companyId +
                    ". Please configure classification rules before uploading transactions.");
        }

        String industryCodeNormalized = merchantIndustryCode.trim().toUpperCase();
        List<RuleClassifierDTO> matched = classifiers.stream()
                .filter(c -> isRuleMatched(industryCodeNormalized, c))
                .toList();

        if (matched.isEmpty()) {
            return UsageValue.unmatched(usageCode, usageName);
        }

        markRuleAsUsed(companyId, industryCodeNormalized, usedRuleKeys);

        List<String> matchedCodes = matched.stream()
                .map(RuleClassifierDTO::getCode)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        List<String> matchedCategories = matched.stream()
                .map(RuleClassifierDTO::getCategory)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        String finalUsageCode = mergeMultiValue(usageCode, matchedCodes);
        String finalUsageName = mergeMultiValue(usageName, matchedCategories);
        return UsageValue.ruleMatched(finalUsageCode, finalUsageName);
    }

    private void markRuleAsUsed(UUID companyId, String industryCodeNormalized, Set<String> usedRuleKeys) {
        if (companyId == null || industryCodeNormalized == null || industryCodeNormalized.isBlank()) {
            return;
        }

        String ruleKey = companyId + "::" + industryCodeNormalized;
        if (usedRuleKeys != null && !usedRuleKeys.add(ruleKey)) {
            return;
        }

        ruleRepo.markRulesAsUsedByCompanyIdAndIndustryCode(companyId, industryCodeNormalized);
    }

    private UsageValue resolveUsageValueByAi(Row row, Map<String, Integer> headerMap, DataFormatter formatter, UUID companyId,
                                             Map<UUID, List<RuleClassifierDTO>> rulesCache,
                                             Map<UUID, BotConfigRequest.Config> botConfigCache,
                                             Map<String, AiFallbackService.AiFallbackResult> aiResultCache,
                                             UsageValue current,
                                             boolean disambiguateMultiCode) {
        Map<String, String> rowSnapshot = extractRowSnapshot(row, headerMap, formatter);
        if (rowSnapshot.isEmpty()) {
            return null;
        }
        String merchantName = rowSnapshot.get("merchant_name");
        String merchantIndustryName = rowSnapshot.get("merchant_industry_name");
        if ((merchantName == null || merchantName.isBlank())
                && (merchantIndustryName == null || merchantIndustryName.isBlank())) {
            return null;
        }

        List<RuleClassifierDTO> classifiers = rulesCache.computeIfAbsent(
                companyId, ruleRepo::getRuleClassifiersByCompanyId
        );
        if (classifiers == null || classifiers.isEmpty()) {
            return null;
        }
        List<RuleClassifierDTO> matchedClassifiers = filterClassifiersForAi(rowSnapshot, classifiers);
        List<RuleClassifierDTO> aiContexts = matchedClassifiers.isEmpty()
                ? selectFallbackAiContexts(classifiers)
                : matchedClassifiers;
        if (aiContexts.isEmpty()) {
            return null;
        }

        // When disambiguating a rule-matched multi-label result, constrain AI context to only the
        // specific candidate classifiers whose codes appear in the multi-label output. This gives
        // the AI a focused view: "here are the N codes rule-based found — pick 1 or 2."
        if (disambiguateMultiCode && current.matchedByRule()) {
            List<String> candidateCodes = splitMultiValue(current.usageCode());
            if (!candidateCodes.isEmpty()) {
                List<RuleClassifierDTO> disambiguationContexts = classifiers.stream()
                        .filter(c -> c != null
                                && c.getCode() != null
                                && !c.getCode().isBlank()
                                && candidateCodes.contains(c.getCode().trim()))
                        .toList();
                if (!disambiguationContexts.isEmpty()) {
                    aiContexts = disambiguationContexts;
                }
            }
        }

        String cacheKey = buildAiCacheKey(companyId, rowSnapshot);
        if (aiResultCache.containsKey(cacheKey)) {
            AiFallbackService.AiFallbackResult cached = aiResultCache.get(cacheKey);
            return toAiUsageValue(current, cached, disambiguateMultiCode);
        }

        BotConfigRequest.Config botConfig = resolveBotConfig(companyId, botConfigCache);
        String promptTemplate = botConfig.getSystemPrompt();
        AiFallbackService.AiFallbackResult aiResult = aiFallbackService.classify(rowSnapshot, aiContexts, promptTemplate, botConfig);
        if (aiResult == null) {
            return inferUsageValueFromContexts(current, rowSnapshot, aiContexts);
        }

        aiResultCache.put(cacheKey, aiResult);
        return toAiUsageValue(current, aiResult, disambiguateMultiCode);
    }

    private UsageValue inferUsageValueFromContexts(UsageValue current, Map<String, String> rowSnapshot, List<RuleClassifierDTO> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }
        String industryCode = rowSnapshot.getOrDefault("merchant_industry_code", "").trim().toUpperCase();

        RuleClassifierDTO best = contexts.stream()
                .filter(c -> c != null)
                .filter(c -> c.getCode() != null && !c.getCode().isBlank())
                .filter(c -> c.getCategory() != null && !c.getCategory().isBlank())
                .filter(c -> !industryCode.isBlank()
                        && c.getMerchantIndustryCode() != null
                        && industryCode.equalsIgnoreCase(c.getMerchantIndustryCode().trim()))
                .findFirst()
                .orElseGet(() -> contexts.stream()
                        .filter(c -> c != null)
                        .filter(c -> c.getCode() != null && !c.getCode().isBlank())
                        .filter(c -> c.getCategory() != null && !c.getCategory().isBlank())
                        .findFirst()
                        .orElse(null));
        if (best == null) {
            return null;
        }

        String mergedCode = mergeMultiValue(current.usageCode(), List.of(best.getCode().trim()));
        String mergedName = mergeMultiValue(current.usageName(), List.of(best.getCategory().trim()));
        return UsageValue.aiMatched(mergedCode, mergedName, "AI response empty; filled from nearest rule context.");
    }

    private UsageValue toAiUsageValue(UsageValue current, AiFallbackService.AiFallbackResult aiResult, boolean disambiguateMultiCode) {
        if (aiResult == null) {
            return null;
        }
        if (disambiguateMultiCode) {
            List<String> aiCodes = splitMultiValue(aiResult.usageCode());
            List<String> aiNames = splitMultiValue(aiResult.usageName());
            String resolvedCode = joinLimited(aiCodes, 2);
            String resolvedName = joinLimited(aiNames, 2);
            if (resolvedCode == null) {
                resolvedCode = joinLimited(splitMultiValue(current.usageCode()), 2);
            }
            if (resolvedName == null) {
                resolvedName = joinLimited(splitMultiValue(current.usageName()), 2);
            }
            String reason = aiResult.reason() == null ? "Rule-based matched; AI narrowed down multi-label codes." : aiResult.reason();
            return current.matchedByRule()
                    ? UsageValue.ruleAiDisambiguated(resolvedCode, resolvedName, reason)
                    : UsageValue.aiMatched(resolvedCode, resolvedName, reason);
        }
        String mergedCode = mergeMultiValue(current.usageCode(), splitMultiValue(aiResult.usageCode()));
        String mergedName = mergeMultiValue(current.usageName(), splitMultiValue(aiResult.usageName()));
        String reason = aiResult.reason() == null ? "AI fallback classification succeeded." : aiResult.reason();
        return UsageValue.aiMatched(mergedCode, mergedName, reason);
    }

    private String buildAiCacheKey(UUID companyId, Map<String, String> rowSnapshot) {
        String merchant = normalizeMatcherText(rowSnapshot.get("merchant_name"));
        String industryCode = rowSnapshot.getOrDefault("merchant_industry_code", "").trim().toUpperCase();
        String taxType = normalizeMatcherText(rowSnapshot.get("tax_type"));
        String amountBucket = amountBucket(rowSnapshot.get("supply_amount"));
        return companyId + "::" + merchant + "::" + industryCode + "::" + taxType + "::" + amountBucket;
    }

    private List<RuleClassifierDTO> filterClassifiersForAi(Map<String, String> rowSnapshot, List<RuleClassifierDTO> classifiers) {
        String industryCode = rowSnapshot.getOrDefault("merchant_industry_code", "").trim().toUpperCase();
        if (industryCode.isBlank()) {
            return List.of();
        }
        return classifiers.stream()
                .filter(c -> c != null
                        && c.getMerchantIndustryCode() != null
                        && industryCode.equalsIgnoreCase(c.getMerchantIndustryCode().trim()))
                .toList();
    }

    private List<RuleClassifierDTO> selectFallbackAiContexts(List<RuleClassifierDTO> classifiers) {
        // Deduplicate by category code so the AI always sees every distinct account
        // in the company's chart of accounts, regardless of how many rules exist.
        // Previously this was limited to the first 30 rules, which caused categories
        // appearing after position 30 to be invisible to the AI.
        LinkedHashMap<String, RuleClassifierDTO> byCode = new java.util.LinkedHashMap<>();
        for (RuleClassifierDTO c : classifiers) {
            if (c != null
                    && c.getCode() != null && !c.getCode().isBlank()
                    && c.getCategory() != null && !c.getCategory().isBlank()) {
                byCode.putIfAbsent(c.getCode().trim(), c);
            }
        }
        return new ArrayList<>(byCode.values());
    }

    private String amountBucket(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return "na";
        }
        String digits = rawAmount.replaceAll("[^0-9-]", "");
        if (digits.isBlank()) {
            return "na";
        }
        try {
            long value = Long.parseLong(digits);
            long bucket = (value / 50000L) * 50000L;
            return Long.toString(bucket);
        } catch (NumberFormatException e) {
            return "na";
        }
    }

    private BotConfigRequest.Config resolveBotConfig(UUID companyId, Map<UUID, BotConfigRequest.Config> botConfigCache) {
        if (botConfigCache.containsKey(companyId)) {
            return botConfigCache.get(companyId);
        }

        BotConfigRequest.Config resolved = BotConfigDefaults.defaultConfig();
        String latestConfigJson = botConfigRepo.getLatestConfigJsonByCompanyId(companyId);
        if (latestConfigJson != null && !latestConfigJson.isBlank()) {
            try {
                BotConfigRequest.Config parsed = objectMapper.readValue(latestConfigJson, BotConfigRequest.Config.class);
                BotConfigRequest.Config defaults = BotConfigDefaults.defaultConfig();
                resolved = BotConfigRequest.Config.builder()
                        .provider(parsed.getProvider() == null ? defaults.getProvider() : parsed.getProvider())
                        .modelName(firstNonBlank(parsed.getModelName(), defaults.getModelName()))
                        .temperature(parsed.getTemperature() == null ? defaults.getTemperature() : parsed.getTemperature())
                        .apiKey(firstNonBlank(parsed.getApiKey(), defaults.getApiKey()))
                        .systemPrompt(firstNonBlank(parsed.getSystemPrompt(), defaults.getSystemPrompt()))
                        .build();
            } catch (Exception ignored) {
                resolved = BotConfigDefaults.defaultConfig();
            }
        }

        botConfigCache.put(companyId, resolved);
        return resolved;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private static final List<String> AI_SNAPSHOT_HEADERS = List.of(
            "approval_date",
            "approval_time",
            "merchant_name",
            "merchant_industry_code",
            "merchant_industry_name",
            "merchant_business_registration_number",
            "supply_amount",
            "vat_amount",
            "tax_type"
    );
    private static final java.util.Set<String> AI_CONTEXT_EXCLUDED_HEADERS = java.util.Set.of(
            "pk",
            "user_tx_id",
            "writer_tx_id"
    );

    private Map<String, String> extractRowSnapshot(Row row, Map<String, Integer> headerMap, DataFormatter formatter) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String header : AI_SNAPSHOT_HEADERS) {
            if (AI_CONTEXT_EXCLUDED_HEADERS.contains(header)) {
                continue;
            }
            String value = getCellValue(row, headerMap, formatter, header);
            if (value != null && !value.isBlank() && !isAbstractIdLike(value)) {
                snapshot.put(header, value);
            }
        }
        return snapshot;
    }

    private boolean isAbstractIdLike(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.matches("(?i)^[A-Z]{2,}\\+[A-Z]+\\d+:[A-Z]+\\d+$")
                || normalized.matches("(?i)^[A-Z]+\\d+:[A-Z]+\\d+$");
    }

    // Pattern Detection using Rule-based on column: 가맹점업종코드 (exact CHAR(5) match)
    private boolean isRuleMatched(String industryCodeNormalized, RuleClassifierDTO classifier) {
        if (industryCodeNormalized == null || industryCodeNormalized.isBlank()) {
            return false;
        }
        String ruleCode = classifier.getMerchantIndustryCode();
        if (ruleCode == null || ruleCode.isBlank()) {
            return false;
        }
        return industryCodeNormalized.equalsIgnoreCase(ruleCode.trim());
    }

    private boolean textMatched(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return false;
        }
        return left.contains(right) || right.contains(left);
    }

    private String normalizeMatcherText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\uFEFF", "")
                .replaceAll("[^0-9a-zA-Z\\uAC00-\\uD7A3]+", "")
                .toLowerCase();
    }

    private String mergeMultiValue(String existing, List<String> additions) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            merged.addAll(Arrays.stream(existing.split("[,;/|]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList());
        }
        if (additions != null) {
            merged.addAll(additions.stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList());
        }
        if (merged.isEmpty()) {
            return null;
        }
        return String.join(",", merged);
    }

    private boolean hasMultiLabel(String value) {
        return splitMultiValue(value).size() > 1;
    }

    private String joinLimited(List<String> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return null;
        }
        List<String> limited = values.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(limit)
                .toList();
        if (limited.isEmpty()) {
            return null;
        }
        return String.join(",", limited);
    }

    private void upsertRuleCategoryFromRow(UUID companyId, String merchantIndustryCode, String merchantIndustryName, String usageCodeRaw, String usageNameRaw) {

        if (companyId == null || merchantIndustryCode == null || merchantIndustryCode.isBlank()) {
            return;
        }
        if (usageCodeRaw == null || usageCodeRaw.isBlank() || usageNameRaw == null || usageNameRaw.isBlank()) {
            return;
        }

        List<String> codes = splitMultiValue(usageCodeRaw);
        List<String> names = splitMultiValue(usageNameRaw);
        if (codes.isEmpty() || names.isEmpty()) {
            return;
        }

        String normalizedCode = merchantIndustryCode.trim().toUpperCase();
        String normalizedName = merchantIndustryName == null ? normalizedCode : merchantIndustryName.trim();
        if (normalizedName.isBlank()) {
            normalizedName = normalizedCode;
        }

        RuleDTO rule = ruleRepo.findByCompanyIdAndIndustryCode(companyId, normalizedCode);
        if (rule == null) {
            rule = ruleRepo.createRule(
                    RuleRequest.builder()
                            .companyId(companyId)
                            .merchantIndustryCode(normalizedCode)
                            .merchantIndustryName(normalizedName)
                            .categoryIds(Collections.emptyList())
                            .description("auto-trained-from-upload")
                            .build()
            );
        }

        List<Pair> pairs = pairCodesAndNames(codes, names);
        for (Pair pair : pairs) {
            String code = pair.code();
            String categoryName = pair.name();
            if (!code.matches("^[A-Za-z0-9]{5}$")) {
                continue;
            }

            CategoryDTO category = findOrCreateCategory(companyId, code, categoryName);
            categoryRepo.markCategoryAsUsed(category.getCategoryId());
            ruleRepo.createRuleCategoryMappingIgnoreConflict(rule.getRuleId(), category.getCategoryId());
        }
    }

    private CategoryDTO findOrCreateCategory(UUID companyId, String code, String categoryName) {
        CategoryDTO byCode = categoryRepo.findByCompanyIdAndCode(companyId, code);
        if (byCode != null) {
            return byCode;
        }

        CategoryDTO byCategory = categoryRepo.findByCompanyIdAndCategory(companyId, categoryName);
        if (byCategory != null) {
            return byCategory;
        }

        return categoryRepo.createCategory(
                CategoryRequest.builder()
                        .companyId(companyId)
                        .code(code)
                        .category(categoryName)
                        .build()
        );
    }

    private List<String> splitMultiValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;/|]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private List<Pair> pairCodesAndNames(List<String> codes, List<String> names) {
        List<Pair> result = new ArrayList<>();
        if (codes.size() == names.size()) {
            for (int i = 0; i < codes.size(); i++) {
                result.add(new Pair(codes.get(i), names.get(i)));
            }
            return result;
        }
        if (codes.size() == 1) {
            for (String name : names) {
                result.add(new Pair(codes.getFirst(), name));
            }
            return result;
        }
        if (names.size() == 1) {
            for (String code : codes) {
                result.add(new Pair(code, names.getFirst()));
            }
            return result;
        }
        int min = Math.min(codes.size(), names.size());
        for (int i = 0; i < min; i++) {
            result.add(new Pair(codes.get(i), names.get(i)));
        }
        return result;
    }

    private void applyUsageValueToSheet(Row row, Map<String, Integer> headerMap, UsageValue usageValue) {
        if (usageValue == null || row == null) {
            return;
        }
        Integer usageCodeCol = headerMap.get(USAGE_CODE_HEADER);
        Integer usageNameCol = headerMap.get(USAGE_NAME_HEADER);
        Integer methodCol = headerMap.get(METHOD_HEADER);
        Integer descriptionCol = headerMap.get(DESCRIPTION_HEADER);

        if (usageCodeCol != null && usageValue.usageCode() != null) {
            row.createCell(usageCodeCol).setCellValue(usageValue.usageCode());
        }
        if (usageNameCol != null && usageValue.usageName() != null) {
            row.createCell(usageNameCol).setCellValue(usageValue.usageName());
        }
        if (methodCol != null && usageValue.method() != null && !usageValue.method().isBlank()) {
            row.createCell(methodCol).setCellValue(usageValue.method());
        }
        if (descriptionCol != null && usageValue.description() != null && !usageValue.description().isBlank()) {
            row.createCell(descriptionCol).setCellValue(usageValue.description());
        }
    }

    private void ensureUsageAndMethodColumns(Row headerRow, Map<String, Integer> headerMap) {
        if (headerRow == null) {
            return;
        }

        short nextCol = headerRow.getLastCellNum() < 0 ? 0 : headerRow.getLastCellNum();
        if (!headerMap.containsKey(USAGE_CODE_HEADER)) {
            headerMap.put(USAGE_CODE_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("용도코드");
            nextCol++;
        }
        if (!headerMap.containsKey(USAGE_NAME_HEADER)) {
            headerMap.put(USAGE_NAME_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("용도명");
            nextCol++;
        }
        if (!headerMap.containsKey(METHOD_HEADER)) {
            headerMap.put(METHOD_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("방법");
            nextCol++;
        }
        if (!headerMap.containsKey(DESCRIPTION_HEADER)) {
            headerMap.put(DESCRIPTION_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("Reason");
        }
    }

    private void ensureUsageAndMethodColumnsSafe(Row headerRow, Map<String, Integer> headerMap) {
        if (headerRow == null) {
            return;
        }

        short nextCol = headerRow.getLastCellNum() < 0 ? 0 : headerRow.getLastCellNum();
        if (!headerMap.containsKey(USAGE_CODE_HEADER)) {
            headerMap.put(USAGE_CODE_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("용도코드");
            nextCol++;
        }
        if (!headerMap.containsKey(USAGE_NAME_HEADER)) {
            headerMap.put(USAGE_NAME_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("용도명");
            nextCol++;
        }
        if (!headerMap.containsKey(METHOD_HEADER)) {
            headerMap.put(METHOD_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("방법");
            nextCol++;
        }
        if (!headerMap.containsKey(DESCRIPTION_HEADER)) {
            headerMap.put(DESCRIPTION_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("Reason");
        }
    }

    private FileStorageResponse storeEnrichedWorkbook(Workbook workbook, String sheetName, String originalFileName) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            String fileName = (originalFileName == null || originalFileName.isBlank())
                    ? "transactions.xlsx"
                    : originalFileName;
            return fileStorageService.storeBytes(
                    outputStream.toByteArray(),
                    fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    com.api.bizplay_classifier_api.model.enums.FileType.OUTPUT
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to store enriched Excel file.", e);
        }
    }

    private TransactionRequest parseRowToRequest(Row row, Map<String, Integer> headerMap, DataFormatter formatter,
                                                 int rowIndex, UUID companyId, String derivedUsageCode) {
        return TransactionRequest.builder()
                .companyId(companyId)
                .approvalDate(getCellValue(row, headerMap, formatter, "approval_date"))
                .approvalTime(getCellValue(row, headerMap, formatter, "approval_time"))
                .merchantName(getCellValue(row, headerMap, formatter, "merchant_name"))
                .merchantIndustryCode(getCellValue(row, headerMap, formatter, "merchant_industry_code"))
                .merchantIndustryName(getCellValue(row, headerMap, formatter, "merchant_industry_name"))
                .merchantBusinessRegistrationNumber(getCellValue(row, headerMap, formatter, "merchant_business_registration_number"))
                .supplyAmount(parseInteger(getCellValue(row, headerMap, formatter, "supply_amount"), rowIndex, "supply_amount"))
                .vatAmount(parseInteger(getCellValue(row, headerMap, formatter, "vat_amount"), rowIndex, "vat_amount"))
                .taxType(getCellValue(row, headerMap, formatter, "tax_type"))
                .fieldName1(derivedUsageCode != null ? derivedUsageCode : getCellValue(row, headerMap, formatter, "field_name1"))
                .pk(getCellValue(row, headerMap, formatter, "pk"))
                .userTxId(getCellValue(row, headerMap, formatter, "user_tx_id"))
                .writerTxId(getCellValue(row, headerMap, formatter, "writer_tx_id"))
                .build();
    }

    private Integer parseInteger(String value, int rowIndex, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + (rowIndex + 1) + ": " + fieldName + " must be an integer.");
        }
    }

    private String getCellValue(Row row, Map<String, Integer> headerMap, DataFormatter formatter, String header) {
        if (!ALL_HEADERS.contains(header) || !headerMap.containsKey(header)) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(headerMap.get(header))).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            if (i >= 0) {
                String value = formatter.formatCellValue(row.getCell(i)).trim();
                if (!value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private record HeaderParseResult(int headerRowIndex, Map<String, Integer> headerMap) {
    }

    private record UsageValue(String usageCode, String usageName, boolean matchedByRule, boolean matchedByAi,
                              String method, String description) {
        private static UsageValue unmatched(String usageCode, String usageName) {
            return new UsageValue(usageCode, usageName, false, false, "", "");
        }

        private static UsageValue ruleMatched(String usageCode, String usageName) {
            return new UsageValue(usageCode, usageName, true, false, "Rule-based", "");
        }

        private static UsageValue aiMatched(String usageCode, String usageName, String reason) {
            String message = (reason == null || reason.isBlank()) ? "AI fallback classification succeeded." : reason;
            return new UsageValue(usageCode, usageName, false, true, "AI", message);
        }

        private static UsageValue ruleAiDisambiguated(String usageCode, String usageName, String reason) {
            String message = (reason == null || reason.isBlank()) ? "Rule-based matched; AI narrowed down multi-label codes." : reason;
            return new UsageValue(usageCode, usageName, true, true, "Rule+AI", message);
        }
    }

    private record Pair(String code, String name) {
    }

    // -------------------------------------------------------------------------
    // Manual row-level patch (UI verify / correct 용도코드)
    // -------------------------------------------------------------------------

    @Override
    public FileRowPatchResponse patchFileRows(UUID fileId, FileRowPatchRequest request) {
        com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO fileRecord =
                fileUploadHistoryRepo.getFileById(fileId);
        if (fileRecord == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        UUID companyId = request.getCompanyId();

        byte[] originalBytes;
        try {
            org.springframework.core.io.Resource resource =
                    fileStorageService.loadAsResource(fileRecord.getStoredFileName());
            originalBytes = resource.getInputStream().readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load stored file.", e);
        }

        try (java.io.InputStream inputStream = new ByteArrayInputStream(originalBytes);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            // Resolve sheet
            String sheetName = fileRecord.getSheetName();
            Sheet sheet;
            if (sheetName != null && !sheetName.isBlank()) {
                sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    throw new IllegalArgumentException("Sheet not found: " + sheetName);
                }
            } else {
                sheet = workbook.getSheetAt(0);
            }

            DataFormatter formatter = new DataFormatter();
            HeaderParseResult headerParseResult = findHeaderMap(sheet, formatter);
            Map<String, Integer> headerMap = headerParseResult.headerMap();
            int headerRowIndex = headerParseResult.headerRowIndex();

            // Ensure output columns exist (safe — no-op if already present)
            ensureUsageAndMethodColumnsSafe(sheet.getRow(headerRowIndex), headerMap);

            Integer usageCodeCol  = headerMap.get(USAGE_CODE_HEADER);
            Integer usageNameCol  = headerMap.get(USAGE_NAME_HEADER);
            Integer methodCol     = headerMap.get(METHOD_HEADER);
            Integer descriptionCol = headerMap.get(DESCRIPTION_HEADER);

            int updatedCount = 0;
            List<Integer> skippedRows = new ArrayList<>();

            for (com.api.bizplay_classifier_api.model.request.RowCellUpdateRequest update : request.getUpdates()) {
                // rowIndex is 1-based; translate to absolute Excel row index
                int excelRowIndex = headerRowIndex + update.getRowIndex();
                Row row = sheet.getRow(excelRowIndex);
                if (row == null) {
                    skippedRows.add(update.getRowIndex());
                    continue;
                }

                String newCode = update.getUsageCode().trim();
                com.api.bizplay_classifier_api.model.dto.CategoryDTO category =
                        categoryRepo.findByCompanyIdAndCode(companyId, newCode);
                if (category == null) {
                    // Code not found in this company's chart of accounts — skip
                    skippedRows.add(update.getRowIndex());
                    continue;
                }

                // 용도코드
                if (usageCodeCol != null) {
                    row.createCell(usageCodeCol).setCellValue(newCode);
                }
                // 용도명 — aligned from category master
                if (usageNameCol != null) {
                    row.createCell(usageNameCol).setCellValue(category.getCategory());
                }
                // 방법 → "Updated"
                if (methodCol != null) {
                    row.createCell(methodCol).setCellValue("Updated");
                }
                // Reason → cleared
                if (descriptionCol != null) {
                    row.createCell(descriptionCol).setCellValue("");
                }

                updatedCount++;
            }

            // Write back to bytes and overwrite the same object in MinIO
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            fileStorageService.replaceBytes(
                    fileRecord.getStoredFileName(),
                    out.toByteArray(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );

            return FileRowPatchResponse.builder()
                    .fileId(fileId)
                    .totalRequested(request.getUpdates().size())
                    .updatedRows(updatedCount)
                    .skippedRows(skippedRows)
                    .enrichedFileUrl(fileRecord.getFileUrl())
                    .storedFileName(fileRecord.getStoredFileName())
                    .build();

        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to patch file rows.", e);
        }
    }
}
