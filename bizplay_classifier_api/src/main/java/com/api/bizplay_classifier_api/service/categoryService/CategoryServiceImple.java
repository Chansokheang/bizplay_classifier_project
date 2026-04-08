package com.api.bizplay_classifier_api.service.categoryService;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.response.CategoryUploadSummaryResponse;
import com.api.bizplay_classifier_api.repository.CategoryRepo;
import com.api.bizplay_classifier_api.service.companyService.CompanyService;
import com.api.bizplay_classifier_api.utils.GetCurrentUser;
import lombok.AllArgsConstructor;
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

@Service
@AllArgsConstructor
public class CategoryServiceImple implements CategoryService {

    private static final String HEADER_CODE = "code";
    private static final String HEADER_CATEGORY = "category";
    private static final String HEADER_IS_USED = "is_used";

    private final CategoryRepo categoryRepo;
    private final CompanyService companyService;
    private final GetCurrentUser getCurrentUser;

    @Override
    public CategoryDTO createCategory(CategoryRequest categoryRequest) {
        ensureCompanyOwnership(categoryRequest.getCompanyId());
        CategoryDTO existedByCode = categoryRepo.findByCompanyIdAndCode(
                categoryRequest.getCompanyId(),
                categoryRequest.getCode()
        );
        if (existedByCode != null) {
            throw new CustomNotFoundException("Category code is already existed.");
        }

//        CategoryDTO existedByCategory = categoryRepo.findByCompanyIdAndCategory(
//                categoryRequest.getCompanyId(),
//                categoryRequest.getCategory()
//        );
//        if (existedByCategory != null) {
//            return existedByCategory;
//        }

        return categoryRepo.createCategory(categoryRequest);
    }

    @Override
    public List<CategoryDTO> getAllCategories() {
        return categoryRepo.getAllCategories();
    }

    @Override
    public List<CategoryDTO> getAllCategoriesByCompanyId(UUID companyId) {
        companyService.getCompanyByCompanyId(companyId);
        return categoryRepo.getAllCategoriesByCompanyId(companyId);
    }

    @Override
    @Transactional
    public CategoryUploadSummaryResponse createCategoriesByExcel(MultipartFile file, UUID companyId, String sheetName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required.");
        }

        ensureCompanyOwnership(companyId);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = resolveSheet(workbook, sheetName);
            DataFormatter formatter = new DataFormatter();
            HeaderParseResult headerResult = findHeaderMap(sheet, formatter);
            Map<String, Integer> headerMap = headerResult.headerMap();
            validateRequiredHeaders(headerMap);

            int totalRows = 0;
            int insertedRows = 0;
            int skippedRows = 0;
            int alreadyExistedRows = 0;

            for (int rowIndex = headerResult.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                totalRows++;

                if (isRowEmpty(row, formatter)) {
                    skippedRows++;
                    continue;
                }

                String code = getCellValue(row, headerMap, formatter, HEADER_CODE);
                String category = getCellValue(row, headerMap, formatter, HEADER_CATEGORY);
                String isUsedRaw = getCellValue(row, headerMap, formatter, HEADER_IS_USED);

                if (code == null || code.isBlank() || category == null || category.isBlank()) {
                    skippedRows++;
                    continue;
                }

                String normalizedCode = code.trim();
                if (!normalizedCode.matches("^[A-Za-z0-9]{5}$")) {
                    throw new IllegalArgumentException(
                            "Row " + (rowIndex + 1) + ": code must be exactly 5 alphanumeric characters."
                    );
                }

                String normalizedCategory = category.trim();
                CategoryDTO existedByCode = categoryRepo.findByCompanyIdAndCode(companyId, normalizedCode);
                CategoryDTO existedByCategory = categoryRepo.findByCompanyIdAndCategory(companyId, normalizedCategory);
                CategoryDTO target = existedByCode != null ? existedByCode : existedByCategory;

                if (target == null) {
                    target = categoryRepo.createCategory(
                            CategoryRequest.builder()
                                    .companyId(companyId)
                                    .code(normalizedCode)
                                    .category(normalizedCategory)
                                    .build()
                    );
                    insertedRows++;
                } else {
                    alreadyExistedRows++;
                }

                if (parseBooleanValue(isUsedRaw)) {
                    categoryRepo.markCategoryAsUsed(target.getCategoryId());
                }
            }

            return CategoryUploadSummaryResponse.builder()
                    .totalRows(totalRows)
                    .insertedRows(insertedRows)
                    .skippedRows(skippedRows)
                    .alreadyExistedRows(alreadyExistedRows)
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read Excel file.", e);
        }
    }

    private void ensureCompanyOwnership(UUID companyId) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = categoryRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName) {
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

        return sheet;
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
                    "Wrong file header format. Required headers: 용도코드(code), 용도명(category). "
                            + "Detected mapped headers: []"
            );
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

        if (containsAny(normalized, "code", "purposecode", "용도코드", "코드")) {
            return HEADER_CODE;
        }
        if (containsAny(normalized, "category", "purpose", "용도명", "카테고리", "분류")) {
            return HEADER_CATEGORY;
        }
        if (containsAny(normalized, "isused", "used", "useyn", "사용여부", "사용유무")) {
            return HEADER_IS_USED;
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
        boolean hasCode = headerMap.containsKey(HEADER_CODE);
        boolean hasCategory = headerMap.containsKey(HEADER_CATEGORY);
        if (!hasCode || !hasCategory) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (!hasCode) missing.add("용도코드(code)");
            if (!hasCategory) missing.add("용도명(category)");
            throw new IllegalArgumentException(
                    "Wrong file header format. Required headers: 용도코드(code), 용도명(category). "
                            + "Missing: " + String.join(", ", missing) + ". "
                            + "Optional: 사용여부(is_used). "
                            + "Detected mapped headers: " + headerMap.keySet()
            );
        }
    }

    private String getCellValue(Row row, Map<String, Integer> headerMap, DataFormatter formatter, String key) {
        Integer colIndex = headerMap.get(key);
        if (colIndex == null || row == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(colIndex)).trim();
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

    private boolean parseBooleanValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("y")
                || normalized.equals("yes")
                || normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("t")
                || normalized.equals("사용");
    }

    private record HeaderParseResult(int headerRowIndex, Map<String, Integer> headerMap) {
    }
}
