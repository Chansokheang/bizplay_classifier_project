package com.api.bizplay_classifier_api.service.ruleService;

import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.RuleUpdateRequest;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.response.DataTrainSummaryResponse;
import com.api.bizplay_classifier_api.repository.CategoryRepo;
import com.api.bizplay_classifier_api.repository.RuleRepo;
import com.api.bizplay_classifier_api.service.companyService.CompanyService;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Log4j2
@Service
@AllArgsConstructor
public class RuleServiceImple implements RuleService {

    private final RuleRepo ruleRepo;
    private final CategoryRepo categoryRepo;
    private final CompanyService companyService;

    @Override
    @Transactional
    public RuleDTO createRule(RuleRequest ruleRequest) {
        companyService.getCompanyByCompanyId(ruleRequest.getCompanyId());
        RuleDTO createdRule = ruleRepo.createRule(ruleRequest);
        if (ruleRequest.getCategoryIds() != null && !ruleRequest.getCategoryIds().isEmpty()) {
            ruleRepo.createRuleCategoryMappings(createdRule.getRuleId(), ruleRequest.getCategoryIds());
            for (UUID categoryId : ruleRequest.getCategoryIds()) {
                categoryRepo.markCategoryAsUsed(categoryId);
            }
        }
        return createdRule;
    }

    @Override
    @Transactional
    public RuleDTO updateRuleByRuleId(UUID ruleId, RuleUpdateRequest ruleUpdateRequest) {
        RuleDTO updatedRule = ruleRepo.updateRuleByRuleId(ruleId, ruleUpdateRequest);
        ruleRepo.deleteRuleCategoryMappings(ruleId);
        if (ruleUpdateRequest.getCategoryIds() != null && !ruleUpdateRequest.getCategoryIds().isEmpty()) {
            ruleRepo.createRuleCategoryMappings(ruleId, ruleUpdateRequest.getCategoryIds());
            for (UUID categoryId : ruleUpdateRequest.getCategoryIds()) {
                categoryRepo.markCategoryAsUsed(categoryId);
            }
        }
        return updatedRule;
    }

    @Override
    public List<RuleDTO> getAllRulesByCompanyId(UUID companyId) {
        companyService.getCompanyByCompanyId(companyId);
        return ruleRepo.getAllRulesByCompanyId(companyId);
    }

    @Override
    @Transactional
    public DataTrainSummaryResponse trainRulesFromExcel(MultipartFile file, UUID companyId, String sheetName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required.");
        }

        companyService.getCompanyByCompanyId(companyId);

        try (InputStream inputStream = file.getInputStream();
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
            HeaderParseResult headerResult = findHeaderMap(sheet, formatter);
            Map<String, Integer> headerMap = headerResult.headerMap();
            validateRequiredHeaders(headerMap);

            int totalRows = 0;
            int trainedRows = 0;
            int skippedRows = 0;
            int createdRules = 0;
            int createdCategories = 0;
            int createdMappings = 0;

            for (int rowIndex = headerResult.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                totalRows++;

                if (isRowEmpty(row, formatter)) {
                    skippedRows++;
                    continue;
                }

                String merchantName = getCellValue(row, headerMap, formatter, "merchant_name");
                String code = getCellValue(row, headerMap, formatter, "usage_code");
                String categoryName = getCellValue(row, headerMap, formatter, "usage_name");

                if (merchantName == null || merchantName.isBlank()
                        || code == null || code.isBlank()
                        || categoryName == null || categoryName.isBlank()) {
                    skippedRows++;
                    continue;
                }

                String normalizedCode = code.trim();
                if (!normalizedCode.matches("^[A-Za-z0-9]{5}$")) {
                    skippedRows++;
                    continue;
                }

                CategoryUpsertResult categoryUpsert = findOrCreateCategory(companyId, normalizedCode, categoryName.trim());
                CategoryDTO category = categoryUpsert.category();
                if (categoryUpsert.created()) {
                    createdCategories++;
                }

                RuleDTO rule = ruleRepo.findByCompanyIdAndRuleName(companyId, merchantName.trim());
                if (rule == null) {
                    rule = ruleRepo.createRule(
                            RuleRequest.builder()
                                    .companyId(companyId)
                                    .ruleName(merchantName.trim())
                                    .description("trained-from-data")
                                    .build()
                    );
                    createdRules++;
                }

                int inserted = ruleRepo.createRuleCategoryMappingIgnoreConflict(rule.getRuleId(), category.getCategoryId());
                if (inserted > 0) {
                    createdMappings++;
                }

                categoryRepo.markCategoryAsUsed(category.getCategoryId());
                trainedRows++;
            }

            return DataTrainSummaryResponse.builder()
                    .totalRows(totalRows)
                    .trainedRows(trainedRows)
                    .skippedRows(skippedRows)
                    .createdRules(createdRules)
                    .createdCategories(createdCategories)
                    .createdMappings(createdMappings)
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read Excel file.", e);
        }
    }

    private CategoryUpsertResult findOrCreateCategory(UUID companyId, String code, String categoryName) {
        CategoryDTO byCode = categoryRepo.findByCompanyIdAndCode(companyId, code);
        if (byCode != null) {
            return new CategoryUpsertResult(byCode, false);
        }

        CategoryDTO byCategory = categoryRepo.findByCompanyIdAndCategory(companyId, categoryName);
        if (byCategory != null) {
            return new CategoryUpsertResult(byCategory, false);
        }

        CategoryDTO created = categoryRepo.createCategory(
                CategoryRequest.builder()
                        .companyId(companyId)
                        .code(code)
                        .category(categoryName)
                        .build()
        );
        return new CategoryUpsertResult(created, true);
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
            String canonical = resolveHeader(rawHeader);
            if (canonical != null) {
                headerMap.put(canonical, i);
            }
        }
        return headerMap;
    }

    private String resolveHeader(String rawHeader) {
        String normalized = normalizeHeader(rawHeader);
        if (containsAny(normalized, "가맹점명", "merchantname", "merchant_name")) {
            return "merchant_name";
        }
        if (containsAny(normalized, "용도코드", "usagecode", "fieldname1", "code")) {
            return "usage_code";
        }
        if (containsAny(normalized, "용도명", "usagename", "category", "purpose")) {
            return "usage_name";
        }
        return null;
    }

    private String normalizeHeader(String rawHeader) {
        return rawHeader
                .replace("\uFEFF", "")
                .replaceAll("[\\s_\\-]+", "")
                .toLowerCase();
    }

    private boolean containsAny(String normalized, String... aliases) {
        for (String alias : aliases) {
            if (normalized.contains(alias.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerMap) {
        if (!headerMap.containsKey("merchant_name")
                || !headerMap.containsKey("usage_code")
                || !headerMap.containsKey("usage_name")) {
            throw new IllegalArgumentException(
                    "Missing required headers. Required: 가맹점명, 용도코드, 용도명."
            );
        }
    }

    private String getCellValue(Row row, Map<String, Integer> headerMap, DataFormatter formatter, String key) {
        Integer col = headerMap.get(key);
        if (col == null || row == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(col)).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        short first = row.getFirstCellNum();
        short last = row.getLastCellNum();
        if (first < 0 || last < 0) {
            return true;
        }
        for (int i = first; i < last; i++) {
            String value = formatter.formatCellValue(row.getCell(i)).trim();
            if (!value.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record HeaderParseResult(int headerRowIndex, Map<String, Integer> headerMap) {
    }

    private record CategoryUpsertResult(CategoryDTO category, boolean created) {
    }

}
