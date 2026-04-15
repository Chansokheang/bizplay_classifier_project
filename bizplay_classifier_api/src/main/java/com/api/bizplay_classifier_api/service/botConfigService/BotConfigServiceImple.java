package com.api.bizplay_classifier_api.service.botConfigService;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import com.api.bizplay_classifier_api.model.enums.AiProvider;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.model.request.PromptEnhancementRequest;
import com.api.bizplay_classifier_api.model.response.PromptEnhancementResponse;
import com.api.bizplay_classifier_api.repository.BotConfigRepo;
import com.api.bizplay_classifier_api.repository.FileUploadHistoryRepo;
import com.api.bizplay_classifier_api.service.aiFallbackService.AiFallbackService;
import com.api.bizplay_classifier_api.service.storageService.FileStorageService;
import com.api.bizplay_classifier_api.utils.GetCurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
public class BotConfigServiceImple implements BotConfigService {
    private final BotConfigRepo botConfigRepo;
    private final FileUploadHistoryRepo fileUploadHistoryRepo;
    private final FileStorageService fileStorageService;
    private final GetCurrentUser getCurrentUser;
    private final ObjectMapper objectMapper;
    private final AiFallbackService aiFallbackService;
    // AI model has 8192 token limit, 130 rows = ~8000 tokens (safe limit)
    private static final int MAX_SAMPLE_ROWS = 130;

    @Override
    @Transactional
    public BotConfigDTO createBotConfig(BotConfigRequest botConfigRequest) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = botConfigRepo.existsCompanyByIdAndUserId(botConfigRequest.getCompanyId(), currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + botConfigRequest.getCompanyId());
        }

        String configJson = toConfigJson(botConfigRequest);
        return saveOrUpdateBotConfig(
                botConfigRequest.getCompanyId(),
                BotConfigRequest.builder()
                        .companyId(botConfigRequest.getCompanyId())
                        .config(botConfigRequest.getConfig())
                        .build(),
                configJson
        );
    }

    @Override
    @Transactional
    public BotConfigDTO upsertBotConfig(UUID companyId, BotConfigRequest.Config config) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = botConfigRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }
        BotConfigRequest request = BotConfigRequest.builder()
                .companyId(companyId)
                .config(config)
                .build();
        String configJson = toConfigJson(request);
        return saveOrUpdateBotConfig(companyId, request, configJson);
    }

    @Override
    public BotConfigDTO getLatestBotConfigByCompanyId(UUID companyId) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = botConfigRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }

        BotConfigDTO latest = botConfigRepo.getLatestBotConfigByCompanyId(companyId);
        if (latest == null) {
            return BotConfigDTO.builder()
                    .companyId(companyId)
                    .config(buildDefaultConfig())
                    .build();
        }
        return withParsedConfig(latest);
    }

    @Override
    @Transactional
    public String updatePromptFromLatestTrainingData(UUID companyId, Integer sampleRows) {
        return updatePromptFromLatestTrainingDataWithSource(companyId, sampleRows, null).getPrompt();
    }

    @Override
    @Transactional
    public PromptEnhancementResponse updatePromptFromLatestTrainingDataWithSource(UUID companyId, Integer sampleRows) {
        return updatePromptFromLatestTrainingDataWithSource(companyId, sampleRows, null);
    }

    @Override
    @Transactional
    public PromptEnhancementResponse updatePromptFromLatestTrainingDataWithSource(UUID companyId, Integer sampleRows, PromptEnhancementRequest request) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = botConfigRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }

        Integer effectiveSampleRows = null; // null = use all valid rows
        if (sampleRows != null) {
            if (sampleRows < 1) {
                throw new IllegalArgumentException("sampleRows must be at least 1.");
            }
            effectiveSampleRows = Math.min(sampleRows, MAX_SAMPLE_ROWS);
        }

        FileUploadHistoryDTO latestFile = fileUploadHistoryRepo.getLatestTrainingFileByCompanyId(companyId);
        if (latestFile == null) {
            throw new CustomNotFoundException("No uploaded training file found for company: " + companyId);
        }

        BotConfigRequest.Config baseConfig = resolveBaseConfig(companyId);
        AiProvider provider = request != null && request.getProvider() != null ? request.getProvider() : baseConfig.getProvider();
        PromptBuildResult promptBuild = buildEnhancedPromptFromFile(
                latestFile,
                effectiveSampleRows,
                BotConfigRequest.Config.builder()
                        .provider(provider)
                        .modelName(firstNonBlank(request == null ? null : request.getModelName(), baseConfig.getModelName()))
                        .temperature(request != null && request.getTemperature() != null ? request.getTemperature() : baseConfig.getTemperature())
                        .apiKey(firstNonBlank(request == null ? null : request.getApiKey(), baseConfig.getApiKey()))
                        .systemPrompt(baseConfig.getSystemPrompt())
                        .build()
        );
        String enhancedPrompt = promptBuild.prompt();
        provider = request != null && request.getProvider() != null ? request.getProvider() : baseConfig.getProvider();
        String modelName = firstNonBlank(request == null ? null : request.getModelName(), baseConfig.getModelName());
        Double temperature = request != null && request.getTemperature() != null ? request.getTemperature() : baseConfig.getTemperature();
        String apiKey = firstNonBlank(request == null ? null : request.getApiKey(), baseConfig.getApiKey());

        String configJson = toConfigJson(BotConfigRequest.builder()
                .companyId(companyId)
                .config(BotConfigRequest.Config.builder()
                        .provider(provider)
                        .modelName(modelName)
                        .temperature(temperature)
                        .apiKey(apiKey)
                        .systemPrompt(enhancedPrompt)
                        .build())
                .build());

        saveOrUpdateBotConfig(
                companyId,
                BotConfigRequest.builder()
                        .companyId(companyId)
                        .config(BotConfigRequest.Config.builder()
                                .provider(provider)
                                .modelName(modelName)
                                .temperature(temperature)
                                .apiKey(apiKey)
                                .systemPrompt(enhancedPrompt)
                                .build())
                        .build(),
                configJson
        );
        return PromptEnhancementResponse.builder()
                .prompt(enhancedPrompt)
                .source(promptBuild.source())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PromptEnhancementResponse generatePromptEnhancementPreview(UUID companyId, Integer sampleRows) {
        UUID currentUserId = getCurrentUser.getCurrentUserId();
        int exists = botConfigRepo.existsCompanyByIdAndUserId(companyId, currentUserId);
        if (exists == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }

        Integer effectiveSampleRows = null;
        if (sampleRows != null) {
            if (sampleRows < 1) {
                throw new IllegalArgumentException("sampleRows must be at least 1.");
            }
            effectiveSampleRows = Math.min(sampleRows, MAX_SAMPLE_ROWS);
        }

        FileUploadHistoryDTO latestFile = fileUploadHistoryRepo.getLatestTrainingFileByCompanyId(companyId);
        if (latestFile == null) {
            throw new CustomNotFoundException("No uploaded training file found for company: " + companyId);
        }

        PromptBuildResult promptBuild = buildEnhancedPromptFromFile(latestFile, effectiveSampleRows, resolveBaseConfig(companyId));
        return PromptEnhancementResponse.builder()
                .prompt(promptBuild.prompt())
                .source(promptBuild.source())
                .build();
    }

    @Override
    @Async("aiTaskExecutor")
    @Transactional(readOnly = true)
    public CompletableFuture<PromptEnhancementResponse> generatePromptEnhancementPreviewAsync(UUID companyId, Integer sampleRows) {
        try {
            PromptEnhancementResponse response = generatePromptEnhancementPreview(companyId, sampleRows);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private BotConfigDTO saveOrUpdateBotConfig(UUID companyId, BotConfigRequest request, String configJson) {
        UUID latestBotId = botConfigRepo.findLatestBotIdByCompanyId(companyId);
        if (latestBotId != null) {
            return withParsedConfig(botConfigRepo.updateBotConfigByBotId(latestBotId, configJson));
        }
        return withParsedConfig(botConfigRepo.createBotConfig(request, configJson));
    }

    private String toConfigJson(BotConfigRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getConfig());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize config.");
        }
    }

    private BotConfigDTO withParsedConfig(BotConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getConfig() != null) {
            return dto;
        }

        BotConfigRequest.Config parsedConfig = buildDefaultConfig();
        String rawConfig = dto.getRawConfig();
        if (rawConfig != null && !rawConfig.isBlank()) {
            try {
                BotConfigRequest.Config parsed = objectMapper.readValue(rawConfig, BotConfigRequest.Config.class);
                parsedConfig = mergeWithDefaults(parsed, buildDefaultConfig());
            } catch (Exception ignored) {
                parsedConfig = buildDefaultConfig();
            }
        }

        dto.setConfig(parsedConfig);
        return dto;
    }

    private BotConfigRequest.Config resolveBaseConfig(UUID companyId) {
        String latestConfigJson = botConfigRepo.getLatestConfigJsonByCompanyId(companyId);
        if (latestConfigJson == null || latestConfigJson.isBlank()) {
            return buildDefaultConfig();
        }
        try {
            BotConfigRequest.Config parsed = objectMapper.readValue(latestConfigJson, BotConfigRequest.Config.class);
            return mergeWithDefaults(parsed, buildDefaultConfig());
        } catch (Exception e) {
            return buildDefaultConfig();
        }
    }

    private BotConfigRequest.Config buildDefaultConfig() {
        return BotConfigDefaults.defaultConfig();
    }

    private BotConfigRequest.Config mergeWithDefaults(BotConfigRequest.Config parsed, BotConfigRequest.Config defaults) {
        return BotConfigRequest.Config.builder()
                .provider(parsed.getProvider() == null ? defaults.getProvider() : parsed.getProvider())
                .modelName(firstNonBlank(parsed.getModelName(), defaults.getModelName()))
                .temperature(parsed.getTemperature() == null ? defaults.getTemperature() : parsed.getTemperature())
                .apiKey(firstNonBlank(parsed.getApiKey(), defaults.getApiKey()))
                .systemPrompt(firstNonBlank(parsed.getSystemPrompt(), defaults.getSystemPrompt()))
                .build();
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private PromptBuildResult buildEnhancedPromptFromFile(
            FileUploadHistoryDTO latestFile,
            Integer sampleRows,
            BotConfigRequest.Config aiConfig
    ) {
        Resource resource = fileStorageService.loadAsResource(latestFile.getStoredFileName());
        try (InputStream inputStream = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = resolveSheet(workbook, latestFile.getSheetName());
            DataFormatter formatter = new DataFormatter();
            HeaderParseResult header = findHeaderMap(sheet, formatter);
            Map<String, Integer> headerMap = header.headerMap();

            int maxRow = sheet.getLastRowNum();
            List<Map<String, String>> trainingRows = new ArrayList<>();
            for (int rowIndex = header.headerRowIndex() + 1; rowIndex <= maxRow; rowIndex++) {
                if (sampleRows != null && trainingRows.size() >= sampleRows) {
                    break;
                }
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String merchantName = getCellValue(row, headerMap, formatter, "merchant_name");
                String usageCode = getCellValue(row, headerMap, formatter, "usage_code");
                String usageName = getCellValue(row, headerMap, formatter, "usage_name");
                if (merchantName == null || merchantName.isBlank()
                        || usageCode == null || usageCode.isBlank()
                        || usageName == null || usageName.isBlank()) {
                    continue;
                }

                Map<String, String> rowData = new LinkedHashMap<>();
                rowData.put("merchant_name", merchantName);
                rowData.put("merchant_industry_code", safeCell(row, headerMap, formatter, "merchant_industry_code"));
                rowData.put("merchant_industry_name", safeCell(row, headerMap, formatter, "merchant_industry_name"));
                rowData.put("supply_amount", safeCell(row, headerMap, formatter, "supply_amount"));
                rowData.put("vat_amount", safeCell(row, headerMap, formatter, "vat_amount"));
                rowData.put("usage_code", usageCode);
                rowData.put("usage_name", usageName);
                trainingRows.add(rowData);
            }

            if (trainingRows.isEmpty()) {
                throw new IllegalArgumentException("No valid training rows found in latest uploaded file.");
            }

            String aiGeneratedPrompt = aiFallbackService.generatePrompt(trainingRows, aiConfig);
            if (aiGeneratedPrompt != null && !aiGeneratedPrompt.isBlank()) {
                String normalized = ensurePromptHasDynamicPlaceholders(stripDynamicPlaceholders(aiGeneratedPrompt));
                if (!looksLikeRowDumpPrompt(normalized)) {
                    return new PromptBuildResult(normalized, "AI");
                }
            }

            return new PromptBuildResult(buildConcisePromptFromPatterns(trainingRows), "FALLBACK");
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to enhance prompt from training file.", e);
        }
    }

    private String safeCell(Row row, Map<String, Integer> headerMap, DataFormatter formatter, String key) {
        String value = getCellValue(row, headerMap, formatter, key);
        return value == null ? "" : value;
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
            throw new IllegalArgumentException("Sheet not found in uploaded training file.");
        }
        return first;
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
            throw new IllegalArgumentException("Could not detect header row from training file.");
        }
        return new HeaderParseResult(bestHeaderRowIndex, bestHeaderMap);
    }

    private Map<String, Integer> parseHeaderMap(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> headerMap = new HashMap<>();
        short lastCell = headerRow.getLastCellNum();
        if (lastCell < 0) {
            return headerMap;
        }
        for (int i = 0; i < lastCell; i++) {
            String raw = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (raw.isEmpty()) {
                continue;
            }
            String canonical = resolveHeader(raw);
            if (canonical != null) {
                headerMap.put(canonical, i);
            }
        }
        return headerMap;
    }

    private String resolveHeader(String rawHeader) {
        String normalized = rawHeader
                .replace("\uFEFF", "")
                .replaceAll("[^0-9a-zA-Z\\uAC00-\\uD7A3]+", "")
                .toLowerCase();
        if (containsAny(normalized, "merchantname", "merchant_name", "가맹점명")) {
            return "merchant_name";
        }
        if (containsAny(normalized, "merchantindustrycode", "merchant_industry_code", "가맹점업종코드")) {
            return "merchant_industry_code";
        }
        if (containsAny(normalized, "merchantindustryname", "merchant_industry_name", "가맹점업종명")) {
            return "merchant_industry_name";
        }
        if (containsAny(normalized, "supplyamount", "supply_amount", "공급금액")) {
            return "supply_amount";
        }
        if (containsAny(normalized, "vatamount", "vat_amount", "부가세액")) {
            return "vat_amount";
        }
        if (containsAny(normalized, "usagecode", "fieldname1", "usage_code", "용도코드")) {
            return "usage_code";
        }
        if (containsAny(normalized, "usagename", "usage_name", "용도명")) {
            return "usage_name";
        }
        return null;
    }

    private boolean containsAny(String normalized, String... aliases) {
        for (String alias : aliases) {
            if (normalized.contains(alias.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String getCellValue(Row row, Map<String, Integer> headerMap, DataFormatter formatter, String key) {
        Integer col = headerMap.get(key);
        if (col == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(col)).trim();
        return value.isEmpty() ? null : value;
    }

    private String stripDynamicPlaceholders(String prompt) {
        if (prompt == null) {
            return "";
        }
        String stripped = prompt;
        stripped = stripped.replace("{{accounts_list}}", "").replace("{{examples}}", "");
        return stripped.stripTrailing();
    }

    private String ensurePromptHasDynamicPlaceholders(String basePrompt) {
        String prompt = basePrompt == null ? "" : basePrompt.strip();
        if (!prompt.contains("{{accounts_list}}")) {
            prompt += "\n\n## 회사 계정과목 목록\n{{accounts_list}}";
        }
        if (!prompt.contains("{{examples}}")) {
            prompt += "\n\n{{examples}}";
        }
        return prompt.strip();
    }

    private boolean looksLikeRowDumpPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        int rowLikeLines = 0;
        for (String line : prompt.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("- 가맹점명:") || t.startsWith("- merchant_name:")) {
                rowLikeLines++;
            }
            if (rowLikeLines >= 5) {
                return true;
            }
        }
        return false;
    }

    private String buildConcisePromptFromPatterns(List<Map<String, String>> trainingRows) {
        Map<String, PatternBucket> buckets = new LinkedHashMap<>();
        for (Map<String, String> row : trainingRows) {
            String usageCode = safeText(row.get("usage_code"));
            String usageName = safeText(row.get("usage_name"));
            if (usageCode.isBlank() || usageName.isBlank()) {
                continue;
            }
            String key = usageCode + "|" + usageName;
            PatternBucket bucket = buckets.computeIfAbsent(key, k -> new PatternBucket(usageCode, usageName));
            bucket.count++;
            bucket.addIndustry(safeText(row.get("merchant_industry_name")));
            bucket.addMerchant(safeText(row.get("merchant_name")));
            Long amount = parseAmount(safeText(row.get("supply_amount")));
            if (amount != null) {
                bucket.observeAmount(amount);
            }
        }

        List<PatternBucket> sorted = buckets.values().stream()
                .sorted(Comparator.comparingInt((PatternBucket b) -> b.count).reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("당신은 기업 회계 전문가입니다. 주어진 거래 내역을 분석하여 해당 회사의 계정과목 체계에 맞는 계정과목을 추천하세요.\n\n");
        sb.append("## 분류 규칙\n\n");

        int sectionCount = 0;
        for (PatternBucket bucket : sorted) {
            if (sectionCount >= 12) {
                break;
            }
            sb.append("### ").append(bucket.usageCode).append(" ").append(bucket.usageName).append("\n");
            String industries = topJoined(bucket.industryCounts, 4);
            String merchants = topJoined(bucket.merchantCounts, 4);
            if (!industries.isBlank()) {
                sb.append("- 주요 업종: ").append(industries).append("\n");
            }
            if (!merchants.isBlank()) {
                sb.append("- 주요 가맹점: ").append(merchants).append("\n");
            }
            if (bucket.amountCount > 0) {
                long avg = bucket.amountSum / bucket.amountCount;
                sb.append("- 금액 경향: 평균 ").append(avg).append("원");
                if (bucket.amountMin != Long.MAX_VALUE && bucket.amountMax != Long.MIN_VALUE) {
                    sb.append(" (범위 ").append(bucket.amountMin).append("~").append(bucket.amountMax).append("원)");
                }
                sb.append("\n");
            }
            sb.append("- 학습 빈도: ").append(bucket.count).append("건\n\n");
            sectionCount++;
        }

        sb.append("## 중요 판단 기준\n");
        sb.append("1. 가맹점업종명과 가맹점명을 우선 기준으로 분류합니다.\n");
        sb.append("2. 용도코드와 용도명 매핑은 학습 데이터에서 관측된 조합을 우선 적용합니다.\n");
        sb.append("3. 금액은 보조 신호로만 사용하고, 업종/가맹점 신호와 충돌하면 업종/가맹점을 우선합니다.\n");
        sb.append("4. 반드시 아래 회사 계정과목 목록에서만 선택합니다.\n\n");
        sb.append("## 회사 계정과목 목록\n{{accounts_list}}\n\n{{examples}}");

        return sb.toString();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private Long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9-]", "");
        if (digits.isBlank() || "-".equals(digits)) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String topJoined(Map<String, Integer> counts, int limit) {
        if (counts == null || counts.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> top = new LinkedHashSet<>();
        counts.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> top.add(e.getKey()));
        return String.join(", ", top);
    }

    private static final class PatternBucket {
        private final String usageCode;
        private final String usageName;
        private int count = 0;
        private long amountSum = 0L;
        private int amountCount = 0;
        private long amountMin = Long.MAX_VALUE;
        private long amountMax = Long.MIN_VALUE;
        private final Map<String, Integer> industryCounts = new LinkedHashMap<>();
        private final Map<String, Integer> merchantCounts = new LinkedHashMap<>();

        private PatternBucket(String usageCode, String usageName) {
            this.usageCode = usageCode;
            this.usageName = usageName;
        }

        private void addIndustry(String industry) {
            if (industry == null || industry.isBlank()) {
                return;
            }
            industryCounts.merge(industry, 1, Integer::sum);
        }

        private void addMerchant(String merchant) {
            if (merchant == null || merchant.isBlank()) {
                return;
            }
            merchantCounts.merge(merchant, 1, Integer::sum);
        }

        private void observeAmount(long amount) {
            amountSum += amount;
            amountCount++;
            amountMin = Math.min(amountMin, amount);
            amountMax = Math.max(amountMax, amount);
        }
    }

    private record HeaderParseResult(int headerRowIndex, Map<String, Integer> headerMap) {
    }

    private record PromptBuildResult(String prompt, String source) {
    }
}
