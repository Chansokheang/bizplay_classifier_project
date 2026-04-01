package com.api.bizplay_classifier_api.service.transactionService;

import com.api.bizplay_classifier_api.model.dto.TransactionDTO;
import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;
import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.TransactionRequest;
import com.api.bizplay_classifier_api.model.request.FileUploadHistoryRequest;
import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import com.api.bizplay_classifier_api.model.response.TransactionResponse;
import com.api.bizplay_classifier_api.model.response.TransactionUploadSummaryResponse;
import com.api.bizplay_classifier_api.repository.CategoryRepo;
import com.api.bizplay_classifier_api.repository.FileUploadHistoryRepo;
import com.api.bizplay_classifier_api.repository.RuleRepo;
import com.api.bizplay_classifier_api.service.companyService.CompanyService;
import com.api.bizplay_classifier_api.service.storageService.FileStorageService;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TransactionServiceImple implements TransactionService {

    private final FileUploadHistoryRepo fileUploadHistoryRepo;
    private final RuleRepo ruleRepo;
    private final CategoryRepo categoryRepo;
    private final CompanyService companyService;
    private final FileStorageService fileStorageService;
    private final ModelMapper modelMapper;
    private static final int EXCEL_BATCH_SIZE = 500;
    private static final String USAGE_CODE_HEADER = "field_name1";
    private static final String USAGE_NAME_HEADER = "usage_name";
    private static final String METHOD_HEADER = "method";
    private static final String DESCRIPTION_HEADER = "description";

    private static final List<String> REQUIRED_HEADERS = List.of(
            "approval_date",
            "approval_time",
            "merchant_name",
            "merchant_business_registration_number"
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
            "writer_tx_id"
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
        try (InputStream inputStream = file.getInputStream()) {
            return createTransactionsByExcel(inputStream, defaultCompanyId, sheetName);
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
        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            return createTransactionsByExcel(inputStream, defaultCompanyId, sheetName);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read Excel file bytes.", e);
        }
    }

    private TransactionUploadSummaryResponse createTransactionsByExcel(InputStream inputStream, UUID defaultCompanyId, String sheetName) throws IOException {
        if (defaultCompanyId != null) {
            companyService.getCompanyByCompanyId(defaultCompanyId);
        }

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
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
            ensureUsageAndMethodColumns(sheet.getRow(headerParseResult.headerRowIndex()), headerMap);

            Map<UUID, List<RuleClassifierDTO>> rulesCache = new HashMap<>();
            UUID fileCompanyId = defaultCompanyId;
            int totalRows = 0;
            int skippedRows = 0;
            int insertedRows = 0;
            int ruleMatchedRows = 0;
            int ruleUnmatchedRows = 0;
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
                UsageValue usageValue = resolveUsageValueByMerchantName(row, headerMap, formatter, companyId, rulesCache);
                applyUsageValueToSheet(row, headerMap, usageValue);
                upsertRuleCategoryFromRow(
                        companyId,
                        getCellValue(row, headerMap, formatter, "merchant_name"),
                        usageValue.usageCode(),
                        usageValue.usageName()
                );
                if (usageValue.matchedByRule()) {
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

                // Transaction table is not used anymore.
                // Keep enrichment + file output only.
            }

            FileStorageResponse enrichedFile = storeEnrichedWorkbook(workbook, sheetName);
            com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO fileRecord = fileUploadHistoryRepo.createFileRecord(
                    FileUploadHistoryRequest.builder()
                            .companyId(fileCompanyId)
                            .originalFileName("transactions_enriched.xlsx")
                            .storedFileName(enrichedFile.getStoredFileName())
                            .fileUrl(enrichedFile.getFileUrl())
                            .sheetName(sheetName)
                            .build()
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
                    .ruleUnmatchedRows(ruleUnmatchedRows)
                    .unmatchedMerchantSamples(unmatchedMerchantSamples)
                    .build();
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
            throw new IllegalArgumentException("Could not detect Excel header row.");
        }

        return new HeaderParseResult(bestHeaderRowIndex, bestHeaderMap);
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
            String canonicalHeader = resolveHeader(rawHeader);
            if (canonicalHeader != null) {
                headerMap.put(canonicalHeader, i);
            }
        }
        return headerMap;
    }

    private String resolveHeader(String rawHeader) {
        String normalized = normalizeHeader(rawHeader);

        String mapped = HEADER_ALIASES.get(normalized);
        if (mapped != null) {
            return mapped;
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
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headerMap.containsKey(requiredHeader)) {
                throw new IllegalArgumentException("Missing required header: " + requiredHeader);
            }
        }
        if (defaultCompanyId == null && !headerMap.containsKey("company_id")) {
            throw new IllegalArgumentException("Missing required header: company_id (or provide companyId parameter).");
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
                                                       Map<UUID, List<RuleClassifierDTO>> rulesCache) {
        String usageCode = getCellValue(row, headerMap, formatter, USAGE_CODE_HEADER);
        String usageName = getCellValue(row, headerMap, formatter, USAGE_NAME_HEADER);
        String merchantName = getCellValue(row, headerMap, formatter, "merchant_name");
        if (merchantName == null || merchantName.isBlank()) {
            return new UsageValue(usageCode, usageName, false);
        }

        List<RuleClassifierDTO> classifiers = rulesCache.computeIfAbsent(
                companyId, ruleRepo::getRuleClassifiersByCompanyId
        );
        if (classifiers == null || classifiers.isEmpty()) {
            return new UsageValue(usageCode, usageName, false);
        }

        String merchantNormalized = normalizeMatcherText(merchantName);
        List<RuleClassifierDTO> matched = classifiers.stream()
                .filter(c -> c.getRuleName() != null && !c.getRuleName().isBlank())
                .filter(c -> isRuleMatched(merchantNormalized, c.getRuleName()))
                .toList();

        if (matched.isEmpty()) {
            return new UsageValue(usageCode, usageName, false);
        }

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
        return new UsageValue(finalUsageCode, finalUsageName, true);
    }

    private boolean isRuleMatched(String merchantNormalized, String ruleName) {
        if (merchantNormalized == null || merchantNormalized.isBlank() || ruleName == null || ruleName.isBlank()) {
            return false;
        }
        String ruleNormalized = normalizeMatcherText(ruleName);
        if (ruleNormalized.isBlank()) {
            return false;
        }
        // Bidirectional contains to support cases where one side is abbreviated.
        return merchantNormalized.contains(ruleNormalized) || ruleNormalized.contains(merchantNormalized);
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
        return merged.stream().collect(Collectors.joining(","));
    }

    private void upsertRuleCategoryFromRow(UUID companyId, String merchantName, String usageCodeRaw, String usageNameRaw) {
        if (companyId == null || merchantName == null || merchantName.isBlank()) {
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

        RuleDTO rule = ruleRepo.findByCompanyIdAndRuleName(companyId, merchantName.trim());
        if (rule == null) {
            rule = ruleRepo.createRule(
                    RuleRequest.builder()
                            .companyId(companyId)
                            .ruleName(merchantName.trim())
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
                result.add(new Pair(codes.get(0), name));
            }
            return result;
        }
        if (names.size() == 1) {
            for (String code : codes) {
                result.add(new Pair(code, names.get(0)));
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
        if (methodCol != null) {
            row.createCell(methodCol).setCellValue(usageValue.matchedByRule() ? "Rule-Based" : "");
        }
        if (descriptionCol != null) {
            row.createCell(descriptionCol).setCellValue(usageValue.matchedByRule() ? "Rule-based classification succeeded." : "");
        }
    }

    private void ensureUsageAndMethodColumns(Row headerRow, Map<String, Integer> headerMap) {
        if (headerRow == null) {
            return;
        }

        short nextCol = headerRow.getLastCellNum() < 0 ? 0 : headerRow.getLastCellNum();
        if (!headerMap.containsKey(USAGE_CODE_HEADER)) {
            headerMap.put(USAGE_CODE_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("\uC6A9\uB3C4\uCF54\uB4DC");
            nextCol++;
        }
        if (!headerMap.containsKey(USAGE_NAME_HEADER)) {
            headerMap.put(USAGE_NAME_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("\uC6A9\uB3C4\uBA85");
            nextCol++;
        }
        if (!headerMap.containsKey(METHOD_HEADER)) {
            headerMap.put(METHOD_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("\uBC29\uBC95");
            nextCol++;
        }
        if (!headerMap.containsKey(DESCRIPTION_HEADER)) {
            headerMap.put(DESCRIPTION_HEADER, (int) nextCol);
            headerRow.createCell(nextCol).setCellValue("reason");
        }
    }

    private FileStorageResponse storeEnrichedWorkbook(Workbook workbook, String sheetName) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            String safeSheet = (sheetName == null || sheetName.isBlank()) ? "sheet1" : sheetName.replaceAll("[^0-9a-zA-Z_-]", "_");
            String fileName = "transactions_enriched_" + safeSheet + ".xlsx";
            return fileStorageService.storeBytes(
                    outputStream.toByteArray(),
                    fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
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

    private record UsageValue(String usageCode, String usageName, boolean matchedByRule) {
    }

    private record Pair(String code, String name) {
    }
}
