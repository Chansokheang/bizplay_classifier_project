package com.api.bizplay_classifier_api.service.aiFallbackService;

import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AiFallbackServiceImple implements AiFallbackService {
    private static final Pattern CODE_PATTERN = Pattern.compile("(?i)\\b([A-Z0-9]{5})\\b");
    private static final String DEFAULT_PROMPT_TEMPLATE = """
            당신은 기업 회계 전문가입니다. 주어진 거래 내역을 분석하여 해당 회사의 계정과목 체계에 맞는 계정과목을 정확하게 분류하세요.

            ## 판단 우선순위 (반드시 이 순서를 따르세요)

            ### [1순위] 가맹점명 + 가맹점업종명 — 핵심 판단 기준
            - 가맹점명(merchant_name)과 가맹점업종명(merchant_industry_name)이 분류의 가장 중요한 근거입니다.
            - 두 필드가 존재하면 반드시 이 두 필드를 먼저 분석하여 계정과목을 결정하세요.
            - 예시:
              - 가맹점업종명: 음식점업, 한식, 중식, 양식, 일식 → 식대
              - 가맹점업종명: 숙박업, 호텔 → 출장비
              - 가맹점업종명: 주유소, 주유 → 주유비
              - 가맹점업종명: 영화관, 문화, 레저, 스포츠 → 대외활동비
              - 가맹점업종명: 통신, 인터넷, 전화 → 통신비
              - 가맹점업종명: 자동차, 차량정비, 세차 → 차량유지비
              - 가맹점업종명: 도서, 출판, 인쇄, 문구 → 도서인쇄비
              - 가맹점명에 '재활센터', '복지관', '사회적기업' 등 포함 → 소모품비 또는 대외활동비

            ### [2순위] 규칙 목록 — 회사 전용 분류 기준
            - 아래 규칙 목록(ruleName, merchantName, merchantIndustryName, description)을 1순위 판단의 보조 근거로 사용하세요.
            - 규칙 목록에 일치하는 항목이 있으면 해당 code/category를 사용하세요.

            ### [3순위] 금액·세금구분·날짜 — 보조 참고 정보 (단독 사용 금지)
            - 금액(supply_amount), 세금구분(tax_type), 승인일시(approval_date/time)는 보조 정보입니다.
            - 이 필드만으로 계정과목을 결정하지 마세요.
            - 예: 금액이 20,000원이고 면세라는 이유만으로 '식대'로 분류하지 마세요.
            - 가맹점명·업종명으로 이미 판단이 가능하면 금액·세금구분은 무시해도 됩니다.
            - 보조 정보는 1순위 판단이 불명확할 때만 참고하세요.

            ## 추가 규칙
            - code/category는 반드시 아래 회사 계정과목 목록에서만 선택하세요.
            - 의미적으로 연관성이 명확한 항목만 선택하세요. 억지로 맞추지 마세요.
            - 교통비(버스·택시·지하철·철도 등 교통수단)와 문화·오락·레저 업종을 혼동하지 마세요.
            - 적합한 항목이 없을 때도 목록에서 가장 의미적으로 유사한 항목을 선택하고, reason 필드에 불확실함과 그 근거를 반드시 명시하세요.

            ## 회사 계정과목 목록
            {{accounts_list}}

            {{examples}}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String aiUrl;
    private final String apiKey;
    private final boolean enabled;

    public AiFallbackServiceImple(
            ObjectMapper objectMapper,
            @Value("${app.ai.fallback.url:http://gpu-local.sovanreach.com:9020/api/v1/exaone-357-8b-instruct-awq/chat/completions}") String aiUrl,
            @Value("${app.ai.fallback.api-key:sk-d7a20eb034c847e8994e192b40c69a61}") String apiKey,
            @Value("${app.ai.fallback.enabled:true}") boolean enabled,
            @Value("${app.ai.fallback.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.ai.fallback.read-timeout-ms:8000}") int readTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.aiUrl = aiUrl;
        this.apiKey = apiKey;
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public AiFallbackResult classify(Map<String, String> rowData, List<RuleClassifierDTO> classifiers, String promptTemplate) {
        if (!enabled || aiUrl == null || aiUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (rowData == null || rowData.isEmpty() || classifiers == null || classifiers.isEmpty()) {
            return null;
        }

        List<Map<String, String>> allContexts = classifiers.stream()
                .filter(Objects::nonNull)
                .filter(c -> c.getCode() != null && !c.getCode().isBlank())
                .filter(c -> c.getCategory() != null && !c.getCategory().isBlank())
                .map(c -> {
                    Map<String, String> context = new LinkedHashMap<>();
                    context.put("merchantIndustryCode", safe(c.getMerchantIndustryCode()));
                    context.put("merchantIndustryName", safe(c.getMerchantIndustryName()));
                    context.put("description", safe(c.getDescription()));
                    context.put("code", c.getCode().trim());
                    context.put("category", c.getCategory().trim());
                    return context;
                })
                .distinct()
                .toList();
        if (allContexts.isEmpty()) {
            return null;
        }

        String systemPrompt = buildSystemPrompt(promptTemplate, allContexts);
        String userPrompt = buildUserPrompt(rowData, allContexts);
        Map<String, Object> requestBody = Map.of(
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            byte[] rawResponseBytes = restClient.post()
                    .uri(aiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN)
                    .header("x-api-key", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            String rawResponse = decodeResponseBody(rawResponseBytes);
            String messageContent = extractMessageContent(rawResponse);
            if (messageContent == null || messageContent.isBlank()) {
                return null;
            }

            JsonNode json = parseJsonObject(messageContent);
            if (json == null) {
                return null;
            }

            String code = asText(json, "code");
            String category = asText(json, "category");
            String reason = asText(json, "reason");

            if (code == null && category == null) {
                return null;
            }

            List<String> codeValues = extractCodes(code);
            if (codeValues.isEmpty()) {
                codeValues = extractCodes(category);
            }
            List<String> categoryValues = splitMultiValue(category);
            if (codeValues.isEmpty() && categoryValues.isEmpty()) {
                return null;
            }

            List<Pair> pairs = resolvePairs(codeValues, categoryValues, allContexts);
            if (pairs.isEmpty()) {
                return null;
            }

            String normalizedCodes = joinDistinct(pairs.stream().map(Pair::code).toList());
            String normalizedCategories = joinDistinct(pairs.stream().map(Pair::name).toList());
            if (normalizedCodes == null || normalizedCategories == null) {
                return null;
            }

            return new AiFallbackResult(normalizedCodes, normalizedCategories, reason);
        } catch (Exception e) {
            log.warn("AI fallback classification failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String generatePrompt(List<Map<String, String>> trainingRows) {
        if (!enabled || aiUrl == null || aiUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (trainingRows == null || trainingRows.isEmpty()) {
            return null;
        }

        String trainingData = trainingRows.stream()
                .map(row -> "- 가맹점명: %s | 가맹점업종코드: %s | 가맹점업종명: %s | 공급금액: %s | 부가세액: %s | 용도코드: %s | 용도명: %s"
                        .formatted(
                                row.getOrDefault("merchant_name", ""),
                                row.getOrDefault("merchant_industry_code", ""),
                                row.getOrDefault("merchant_industry_name", ""),
                                row.getOrDefault("supply_amount", ""),
                                row.getOrDefault("vat_amount", ""),
                                row.getOrDefault("usage_code", ""),
                                row.getOrDefault("usage_name", "")
                        ))
                .collect(java.util.stream.Collectors.joining("\n"));

        String systemMessage = """
                당신은 기업 회계 분류 AI 시스템의 프롬프트 생성 전문가입니다.
                아래 학습 데이터를 분석하여, 거래 내역을 계정과목으로 자동 분류하는 시스템 프롬프트를 생성하세요.

                ## 요구사항
                1. 학습 데이터의 패턴(가맹점명, 가맹점업종명, 공급금액, 부가세액 등)을 분석하여 분류 규칙과 힌트를 도출하세요.
                2. 생성할 프롬프트는 AI가 거래 내역을 입력받아 용도코드와 용도명을 예측하는 데 사용됩니다.
                3. 반드시 다음 두 플레이스홀더를 정확히 이 형식 그대로 포함하세요:
                   - {{accounts_list}}: 이 위치에 회사 계정과목 목록이 삽입됩니다
                   - {{examples}}: 이 위치에 규칙 기반 예시가 삽입됩니다
                4. 한국어로 작성하세요.
                5. 시스템 프롬프트 텍스트만 출력하고 다른 설명이나 마크다운 코드블록은 추가하지 마세요.
                """;

        String userMessage = "## 학습 데이터 (%d건)\n".formatted(trainingRows.size())
                + trainingData
                + "\n\n위 학습 데이터를 기반으로 시스템 프롬프트를 생성하세요.";

        Map<String, Object> requestBody = Map.of(
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemMessage),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            byte[] rawResponseBytes = restClient.post()
                    .uri(aiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN)
                    .header("x-api-key", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            String rawResponse = decodeResponseBody(rawResponseBytes);
            String content = extractMessageContent(rawResponse);
            if (content == null || content.isBlank()) {
                return null;
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("AI prompt generation failed: {}", e.getMessage());
            return null;
        }
    }

    private String decodeResponseBody(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return new String(body, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(body);
        }
    }

    private String buildSystemPrompt(String promptTemplate, List<Map<String, String>> contexts) {
        String base = (promptTemplate == null || promptTemplate.isBlank()) ? DEFAULT_PROMPT_TEMPLATE : promptTemplate;
        String accountsList = buildAccountsList(contexts);
        String examples = buildExamples(contexts);
        return base
                .replace("{{accounts_list}}", accountsList)
                .replace("{{examples}}", examples);
    }

    private String buildUserPrompt(Map<String, String> rowData, List<Map<String, String>> contexts) {
        return """
                아래 거래 내역을 분류하세요.

                거래 내역(JSON):
                %s

                규칙 컨텍스트(JSON):
                %s

                반드시 JSON만 반환하세요.
                형식:
                {"code":"XXXXX","category":"계정명","reason":"선택 이유"}
                - 여러 계정이 필요하면: {"code":"XXXXX,YYYYY","category":"계정명1,계정명2","reason":"선택 이유"}
                - code/category는 같은 순서로 매핑하세요.
                - code는 반드시 제공된 회사 계정과목 목록의 코드만 사용하세요.
                - category도 반드시 해당 코드에 대응되는 값만 사용하세요.
                """.formatted(toJson(rowData), toJson(contexts));
    }

    private String buildAccountsList(List<Map<String, String>> contexts) {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        for (Map<String, String> context : contexts) {
            String code = context.get("code");
            String category = context.get("category");
            if (code != null && !code.isBlank() && category != null && !category.isBlank()) {
                entries.add("- %s: %s".formatted(code, category));
            }
        }
        if (entries.isEmpty()) {
            return "- (no account candidates)";
        }
        return String.join("\n", entries);
    }

    private String buildExamples(List<Map<String, String>> contexts) {
        List<String> lines = contexts.stream()
                .map(c -> {
                    String merchantIndustryCode = c.getOrDefault("merchantIndustryCode", "");
                    String merchantIndustryName = c.getOrDefault("merchantIndustryName", "");
                    String description = c.getOrDefault("description", "");
                    String code = c.getOrDefault("code", "");
                    String category = c.getOrDefault("category", "");
                    return "- 가맹점업종코드: %s | 가맹점업종명: %s | 설명: %s | 추천: %s %s"
                            .formatted(merchantIndustryCode, merchantIndustryName, description, code, category);
                })
                .toList();
        if (lines.isEmpty()) {
            return "## 예시\n- (no examples)";
        }
        return "## 예시\n" + String.join("\n", lines);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractMessageContent(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        JsonNode root = parseJsonObject(rawResponse);
        if (root == null || !root.has("choices") || !root.get("choices").isArray() || root.get("choices").isEmpty()) {
            return null;
        }

        JsonNode first = root.get("choices").get(0);
        if (first == null || !first.has("message") || !first.get("message").has("content")) {
            return null;
        }

        JsonNode contentNode = first.get("message").get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        String content = contentNode.asText();
        return content == null || content.isBlank() ? null : content;
    }

    private String extractMessageContentOld(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object firstObj = choices.getFirst();
        if (!(firstObj instanceof Map<?, ?> first)) {
            return null;
        }
        Object messageObj = first.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            return null;
        }
        Object contentObj = message.get("content");
        return contentObj == null ? null : String.valueOf(contentObj);
    }

    private JsonNode parseJsonObject(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String candidate = raw.substring(start, end + 1);
                try {
                    return objectMapper.readTree(candidate);
                } catch (Exception ignored2) {
                    return null;
                }
            }
            return null;
        }
    }

    private String asText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> extractCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        Matcher matcher = CODE_PATTERN.matcher(value);
        while (matcher.find()) {
            codes.add(matcher.group(1).toUpperCase());
        }
        return new ArrayList<>(codes);
    }

    private List<String> splitMultiValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("[,;/|]"))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<Pair> resolvePairs(List<String> codes, List<String> categories, List<Map<String, String>> contexts) {
        List<Pair> resolved = new ArrayList<>();

        if (!codes.isEmpty() && !categories.isEmpty()) {
            List<Pair> aiPairs = pairCodesAndNames(codes, categories);
            for (Pair pair : aiPairs) {
                Pair matched = matchCandidatePair(pair.code(), pair.name(), contexts);
                if (matched != null) {
                    resolved.add(matched);
                }
            }
        } else if (!codes.isEmpty()) {
            for (String code : codes) {
                Pair matched = matchCandidatePair(code, null, contexts);
                if (matched != null) {
                    resolved.add(matched);
                }
            }
        } else {
            for (String category : categories) {
                Pair matched = matchCandidatePair(null, category, contexts);
                if (matched != null) {
                    resolved.add(matched);
                }
            }
        }

        LinkedHashSet<Pair> unique = new LinkedHashSet<>(resolved);
        return new ArrayList<>(unique);
    }

    private Pair matchCandidatePair(String code, String category, List<Map<String, String>> contexts) {
        String codeNorm = code == null ? null : code.trim().toUpperCase();
        String categoryNorm = normalizeComparable(category);

        if (codeNorm != null && !codeNorm.matches("^[A-Z0-9]{5}$")) {
            return null;
        }

        for (Map<String, String> context : contexts) {
            String ctxCode = context.get("code");
            String ctxCategory = context.get("category");
            if (ctxCode == null || ctxCategory == null) {
                continue;
            }

            boolean codeMatched = codeNorm == null || codeNorm.equalsIgnoreCase(ctxCode);
            boolean categoryMatched = categoryNorm == null || categoryNorm.isBlank()
                    || matchesCategoryFlexibly(categoryNorm, normalizeComparable(ctxCategory));

            if (codeMatched && categoryMatched) {
                return new Pair(ctxCode.trim(), ctxCategory.trim());
            }
        }
        return null;
    }

    private boolean matchesCategoryFlexibly(String inputNorm, String candidateNorm) {
        if (inputNorm == null || inputNorm.isBlank() || candidateNorm == null || candidateNorm.isBlank()) {
            return false;
        }
        return inputNorm.equals(candidateNorm)
                || inputNorm.contains(candidateNorm)
                || candidateNorm.contains(inputNorm);
    }

    private String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\uFEFF", "")
                .replaceAll("[^0-9a-zA-Z\\uAC00-\\uD7A3]+", "")
                .toLowerCase();
    }

    private List<Pair> pairCodesAndNames(List<String> codes, List<String> names) {
        if (codes.size() == names.size()) {
            return java.util.stream.IntStream.range(0, codes.size())
                    .mapToObj(i -> new Pair(codes.get(i), names.get(i)))
                    .toList();
        }
        if (codes.size() == 1) {
            return names.stream().map(n -> new Pair(codes.getFirst(), n)).toList();
        }
        if (names.size() == 1) {
            return codes.stream().map(c -> new Pair(c, names.getFirst())).toList();
        }
        int min = Math.min(codes.size(), names.size());
        return java.util.stream.IntStream.range(0, min)
                .mapToObj(i -> new Pair(codes.get(i), names.get(i)))
                .toList();
    }

    private String joinDistinct(List<String> values) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                merged.add(value.trim());
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        return String.join(",", merged);
    }

    private record Pair(String code, String name) {
    }
}
