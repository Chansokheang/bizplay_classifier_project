package com.api.bizplay_classifier_api.service.aiFallbackService;

import com.api.bizplay_classifier_api.model.enums.AiProvider;
import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.service.botConfigService.BotConfigDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
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
    private static final int MAX_AI_CONTEXTS = 40;
    private static final String AI_RESPONSE_SCHEMA = """
            {
              "code": "A1004",
              "category": "CATEGORY_NAME",
              "reason": "short reason"
            }
            """;
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
    private final String openAiCompatibleUrl;
    private final String openAiUrl;
    private final String geminiUrlTemplate;
    private final String claudeUrl;
    private final String fallbackApiKey;
    private final boolean enabled;

    public AiFallbackServiceImple(
            ObjectMapper objectMapper,
            @Value("${app.ai.fallback.url:http://gpu-local.sovanreach.com:9020/api/v1/exaone-357-8b-instruct-awq/chat/completions}") String openAiCompatibleUrl,
            @Value("${app.ai.fallback.openai.url:https://api.openai.com/v1/chat/completions}") String openAiUrl,
            @Value("${app.ai.fallback.gemini.url-template:https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent}") String geminiUrlTemplate,
            @Value("${app.ai.fallback.claude.url:https://api.anthropic.com/v1/messages}") String claudeUrl,
            @Value("${app.ai.fallback.api-key:sk-d7a20eb034c847e8994e192b40c69a61}") String apiKey,
            @Value("${app.ai.fallback.enabled:true}") boolean enabled,
            @Value("${app.ai.fallback.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.ai.fallback.read-timeout-ms:8000}") int readTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.openAiCompatibleUrl = openAiCompatibleUrl;
        this.openAiUrl = openAiUrl;
        this.geminiUrlTemplate = geminiUrlTemplate;
        this.claudeUrl = claudeUrl;
        this.fallbackApiKey = apiKey;
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public AiFallbackResult classify(
            Map<String, String> rowData,
            List<RuleClassifierDTO> classifiers,
            String promptTemplate,
            BotConfigRequest.Config aiConfig
    ) {
        ResolvedAiConfig resolvedConfig = resolveConfig(aiConfig);
        if (resolvedConfig == null) {
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

        List<Map<String, String>> aiContexts = selectRelevantContexts(rowData, allContexts);
        String systemPrompt = buildSystemPrompt(promptTemplate, aiContexts);
        String userPrompt = buildUserPrompt(rowData, aiContexts);
        try {
            String messageContent = executePrompt(systemPrompt, userPrompt, resolvedConfig);
            if (messageContent == null || messageContent.isBlank()) {
                log.warn("AI fallback returned empty content on first attempt. provider={}, model={}, contextCount={}/{}",
                        resolvedConfig.provider(), resolvedConfig.modelName(), aiContexts.size(), allContexts.size());
                messageContent = executePrompt(systemPrompt, buildRetryUserPrompt(rowData, aiContexts), resolvedConfig);
            }

            ParseOutcome outcome = parseClassificationResult(messageContent, allContexts);
            if (outcome.result() != null) {
                return outcome.result();
            }

            log.warn("AI fallback returned unusable content. reason={}, provider={}, model={}, content={}",
                    outcome.failureReason(), resolvedConfig.provider(), resolvedConfig.modelName(), abbreviate(messageContent));

            String repairedContent = executePrompt(
                    systemPrompt,
                    buildRepairUserPrompt(rowData, aiContexts, messageContent, outcome.failureReason()),
                    resolvedConfig
            );
            ParseOutcome repairedOutcome = parseClassificationResult(repairedContent, allContexts);
            if (repairedOutcome.result() != null) {
                return repairedOutcome.result();
            }

            log.warn("AI fallback repair attempt failed. reason={}, provider={}, model={}, content={}",
                    repairedOutcome.failureReason(), resolvedConfig.provider(), resolvedConfig.modelName(), abbreviate(repairedContent));
            return null;
        } catch (Exception e) {
            log.warn("AI fallback classification failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String generatePrompt(List<Map<String, String>> trainingRows, BotConfigRequest.Config aiConfig) {
        ResolvedAiConfig resolvedConfig = resolveConfig(aiConfig);
        if (resolvedConfig == null) {
            return null;
        }
        if (trainingRows == null || trainingRows.isEmpty()) {
            return null;
        }

        String trainingData = trainingRows.stream()
                .map(row -> "- ???ル봿???轅붽틓??????類?꼥?? %s | ???ル봿???轅붽틓?????????ㅼ굣?????썹땟???? %s | ???ル봿???轅붽틓?????????ㅼ굣???? %s | ?????雅?퍔瑗??믩궪????ㅻ깹?? %s | ????뉖????ル봿????꿔꺂????壤? %s | ??癲ル슢???????썹땟???? %s | ??癲ル슢???影琉대돗? %s"
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
                ??????? ??????壤굿?怨밸뻸?????????怨쀫뮝力?遊븀빊?AI ??癲?嶺??癲ル슢???먮뙀??????밸븶?????袁ｋ쨨營?????袁⑸즴????????쇈궘?癲???????뽯쨦??
                ?????밸븶??????? ?????????? ???怨쀫뮝力???癲ル슢???援? ?轅몄뫅??????????욱룑?????壤굿??????롪퍓肉??潁?????????????癲????怨쀫뮝力?遊븀빊???棺堉?댆洹ⓦ럹???癲?嶺???????밸븶?????袁ｋ쨨營??????袁⑸즴????癲ル슢?ｅ젆???

                ## ????거??????
                1. ????? ???????????????????????ル봿???轅붽틓??????類?꼥?? ???ル봿???轅붽틓?????????ㅼ굣???? ?????雅?퍔瑗??믩궪????ㅻ깹?? ????뉖????ル봿????꿔꺂????壤????????怨쀫뮝力???癲ル슢???援????怨쀫뮝力?遊븀빊?????????룰눋???????살퓢????????밸븶???癲ル슢?ｅ젆???
                2. ???袁⑸즴?????????밸븶?????袁ｋ쨨營???AI???ル봿?? ?轅몄뫅??????????욱룑????????⑤챷竊??ш끽維뽳쭩??룸챶猶????癲ル슢???????썹땟????? ??癲ル슢???影琉대돗?????????????棺堉?댆洹ⓦ럹????????癲ル슢?????
                3. ??ш끽維뽳쭩???щ쾪??????濚밸Ŧ援?????????????욱룏嶺????? ??꿔꺂?€??⑤슣瑗??????꿔꺂??틝???놁뗄?????얠뺏癲????????癲ル슢?ｅ젆???
                   - {{accounts_list}}: ???????밸븶??????????壤굿??????롪퍓肉??潁????轅붽틓??熬곥끇釉띄춯誘좊???????????癲ル슢?????
                   - {{examples}}: ???????밸븶????????????????泳?뿀???????ㅻ쿋????ル봿?? ???????癲ル슢?????
                4. ???怨뚮뼺??곷㎨???嚥????????癲ル슢?ｅ젆???
                5. ??癲?嶺???????밸븶?????袁ｋ쨨營??????筌뤾쑬已??꿔꺂????븍툖???????????????롪퍓肉???????遊붋???????몄쐾??????轅붽틓??????窺???濚밸Ŧ援? ????썹땟???????癰귙끆????レ죶?? ????살퓢????? ?轅붽틓??????명뒌??
                """;

        String userMessage = "## ????? ?????????(%d??\n".formatted(trainingRows.size())
                + trainingData
                + "\n\n??????? ?????????? ???????泳?뿀???????????癲?嶺???????밸븶?????袁ｋ쨨營??????袁⑸즴????癲ル슢?ｅ젆???";

        try {
            log.info("AI generatePrompt calling: provider={}, url={}, model={}", resolvedConfig.provider(), resolvedConfig.url(), resolvedConfig.modelName());
            String content = executePrompt(systemMessage, userMessage, resolvedConfig);
            if (content == null || content.isBlank()) {
                log.warn("AI generatePrompt returned empty content");
                return null;
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("AI prompt generation failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private ResolvedAiConfig resolveConfig(BotConfigRequest.Config aiConfig) {
        if (!enabled) {
            return null;
        }

        BotConfigRequest.Config effectiveConfig = aiConfig == null ? BotConfigDefaults.defaultConfig() : aiConfig;
        AiProvider provider = effectiveConfig.getProvider() == null
                ? BotConfigDefaults.DEFAULT_PROVIDER
                : effectiveConfig.getProvider();
        String modelName = safe(firstNonBlank(effectiveConfig.getModelName(), BotConfigDefaults.DEFAULT_MODEL_NAME));
        String apiKey = safe(firstNonBlank(effectiveConfig.getApiKey(), fallbackApiKey));
        Double temperature = effectiveConfig.getTemperature() == null
                ? BotConfigDefaults.DEFAULT_TEMPERATURE
                : effectiveConfig.getTemperature();

        if (apiKey.isBlank()) {
            return null;
        }

        return switch (provider) {
            case OPENAI -> openAiUrl == null || openAiUrl.isBlank()
                    ? null
                    : new ResolvedAiConfig(provider, openAiUrl, modelName, temperature, apiKey);
            case GEMINI -> geminiUrlTemplate == null || geminiUrlTemplate.isBlank()
                    ? null
                    : new ResolvedAiConfig(provider, buildGeminiUrl(modelName, apiKey), modelName, temperature, apiKey);
            case CLAUDE -> claudeUrl == null || claudeUrl.isBlank()
                    ? null
                    : new ResolvedAiConfig(provider, claudeUrl, modelName, temperature, apiKey);
            case EXAONE -> openAiCompatibleUrl == null || openAiCompatibleUrl.isBlank()
                    ? null
                    : new ResolvedAiConfig(provider, openAiCompatibleUrl, modelName, temperature, apiKey);
        };
    }

    private String executePrompt(String systemPrompt, String userPrompt, ResolvedAiConfig config) {
        byte[] rawResponseBytes = restClient.post()
                .uri(config.url())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN)
                .headers(headers -> applyHeaders(headers, config))
                .body(buildRequestBody(systemPrompt, userPrompt, config))
                .retrieve()
                .body(byte[].class);

        String rawResponse = decodeResponseBody(rawResponseBytes);
        String messageContent = extractProviderMessageContent(rawResponse, config.provider());
        if ((messageContent == null || messageContent.isBlank()) && rawResponse != null && !rawResponse.isBlank()) {
            log.warn("AI provider returned raw response without usable message content. provider={}, model={}, raw={}",
                    config.provider(), config.modelName(), abbreviate(rawResponse));
        }
        return messageContent;
    }

    private void applyHeaders(org.springframework.http.HttpHeaders headers, ResolvedAiConfig config) {
        switch (config.provider()) {
            case OPENAI -> headers.setBearerAuth(config.apiKey());
            case EXAONE -> headers.set("x-api-key", config.apiKey());
            case CLAUDE -> {
                headers.set("x-api-key", config.apiKey());
                headers.set("anthropic-version", "2023-06-01");
            }
            case GEMINI -> {
            }
        }
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt, ResolvedAiConfig config) {
        return switch (config.provider()) {
            case OPENAI -> buildOpenAiStyleBody(systemPrompt, userPrompt, config);
            case EXAONE -> buildExaoneBody(systemPrompt, userPrompt, config);
            case GEMINI -> buildGeminiBody(systemPrompt, userPrompt, config);
            case CLAUDE -> buildClaudeBody(systemPrompt, userPrompt, config);
        };
    }

    private Map<String, Object> buildExaoneBody(String systemPrompt, String userPrompt, ResolvedAiConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.modelName());
        body.put("temperature", config.temperature());
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        return body;
    }

    private Map<String, Object> buildOpenAiStyleBody(String systemPrompt, String userPrompt, ResolvedAiConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.modelName());
        body.put("temperature", config.temperature());
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        return body;
    }

    private Map<String, Object> buildGeminiBody(String systemPrompt, String userPrompt, ResolvedAiConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
        ));
        body.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt))
                )
        ));
        body.put("generationConfig", Map.of(
                "temperature", config.temperature()
        ));
        return body;
    }

    private Map<String, Object> buildClaudeBody(String systemPrompt, String userPrompt, ResolvedAiConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.modelName());
        body.put("temperature", config.temperature());
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);
        body.put("messages", List.of(
                Map.of("role", "user", "content", userPrompt)
        ));
        return body;
    }

    private String buildGeminiUrl(String modelName, String apiKey) {
        String encodedModel = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String resolvedTemplate = geminiUrlTemplate.replace("{model}", encodedModel);
        String separator = resolvedTemplate.contains("?") ? "&" : "?";
        return resolvedTemplate + separator + "key=" + encodedApiKey;
    }

    private String extractProviderMessageContent(String rawResponse, AiProvider provider) {
        return switch (provider) {
            case GEMINI -> extractGeminiMessageContent(rawResponse);
            case CLAUDE -> extractClaudeMessageContent(rawResponse);
            case OPENAI, EXAONE -> extractOpenAiMessageContent(rawResponse);
        };
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
                Classify the transaction below.

                Transaction row (JSON):
                %s

                Allowed account contexts (JSON):
                %s

                Output structure:
                %s

                Rules:
                - Return exactly one JSON object and nothing else.
                - No markdown, no code block, no prose.
                - code must use only codes from the provided contexts.
                - category must match the selected code exactly from the provided contexts.
                - If multiple results are required, join code values with commas and join category values with commas in the same order.
                - reason must be short and concrete.
                """.formatted(toJson(rowData), toJson(contexts), AI_RESPONSE_SCHEMA);
    }

    private String buildRetryUserPrompt(Map<String, String> rowData, List<Map<String, String>> contexts) {
        return buildUserPrompt(rowData, contexts) + """


                Previous response was empty or invalid.
                Return exactly one JSON object with no extra text.
                """;
    }

    private String buildRepairUserPrompt(
            Map<String, String> rowData,
            List<Map<String, String>> contexts,
            String invalidContent,
            String failureReason
    ) {
        return """
                The previous AI response was invalid. Rewrite it.

                Failure reason:
                %s

                Previous response:
                %s

                Transaction row (JSON):
                %s

                Allowed account contexts (JSON):
                %s

                Output structure:
                %s

                Rules:
                - Return exactly one JSON object and nothing else.
                - No markdown, no code block, no prose.
                - code must use only codes from the provided contexts.
                - category must match the selected code exactly from the provided contexts.
                - If multiple results are required, join code values with commas and join category values with commas in the same order.
                - reason must be short and concrete.
                """.formatted(
                failureReason == null ? "unknown" : failureReason,
                invalidContent == null ? "" : invalidContent,
                toJson(rowData),
                toJson(contexts),
                AI_RESPONSE_SCHEMA
        );
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

    private List<Map<String, String>> selectRelevantContexts(
            Map<String, String> rowData,
            List<Map<String, String>> allContexts
    ) {
        if (allContexts.size() <= MAX_AI_CONTEXTS) {
            return allContexts;
        }

        String merchantIndustryCode = safe(firstNonBlank(
                rowData.get("merchantIndustryCode"),
                rowData.get("merchant_industry_code")
        ));
        String merchantIndustryName = safe(firstNonBlank(
                rowData.get("merchantIndustryName"),
                rowData.get("merchant_industry_name")
        ));
        String merchantName = safe(firstNonBlank(
                rowData.get("merchantName"),
                rowData.get("merchant_name")
        ));
        String description = safe(firstNonBlank(
                rowData.get("description"),
                rowData.get("merchantDescription")
        ));

        List<Map<String, String>> prioritized = allContexts.stream()
                .sorted((left, right) -> Integer.compare(
                        scoreContext(right, merchantIndustryCode, merchantIndustryName, merchantName, description),
                        scoreContext(left, merchantIndustryCode, merchantIndustryName, merchantName, description)
                ))
                .limit(MAX_AI_CONTEXTS)
                .toList();

        log.info("AI fallback reduced contexts from {} to {} for merchantName={}, merchantIndustryCode={}, merchantIndustryName={}",
                allContexts.size(), prioritized.size(), merchantName, merchantIndustryCode, merchantIndustryName);
        return prioritized;
    }

    private int scoreContext(
            Map<String, String> context,
            String merchantIndustryCode,
            String merchantIndustryName,
            String merchantName,
            String description
    ) {
        int score = 0;
        String contextCode = safe(context.get("merchantIndustryCode"));
        String contextIndustryName = safe(context.get("merchantIndustryName"));
        String contextDescription = safe(context.get("description"));
        String contextCategory = safe(context.get("category"));

        if (!merchantIndustryCode.isBlank() && merchantIndustryCode.equalsIgnoreCase(contextCode)) {
            score += 100;
        }
        if (!merchantIndustryName.isBlank() && merchantIndustryName.equalsIgnoreCase(contextIndustryName)) {
            score += 80;
        }
        if (!merchantIndustryName.isBlank() && contextIndustryName.toLowerCase().contains(merchantIndustryName.toLowerCase())) {
            score += 35;
        }
        if (!merchantName.isBlank() && contextDescription.toLowerCase().contains(merchantName.toLowerCase())) {
            score += 25;
        }
        if (!description.isBlank() && contextDescription.toLowerCase().contains(description.toLowerCase())) {
            score += 15;
        }
        if (!merchantIndustryName.isBlank() && contextCategory.toLowerCase().contains(merchantIndustryName.toLowerCase())) {
            score += 10;
        }
        return score;
    }

    private String buildExamples(List<Map<String, String>> contexts) {
        List<String> lines = contexts.stream()
                .map(c -> {
                    String merchantIndustryCode = c.getOrDefault("merchantIndustryCode", "");
                    String merchantIndustryName = c.getOrDefault("merchantIndustryName", "");
                    String description = c.getOrDefault("description", "");
                    String code = c.getOrDefault("code", "");
                    String category = c.getOrDefault("category", "");
                    return "- merchantIndustryCode: %s | merchantIndustryName: %s | description: %s | recommendation: %s %s"
                            .formatted(merchantIndustryCode, merchantIndustryName, description, code, category);
                })
                .toList();
        if (lines.isEmpty()) {
            return "## Examples\n- (no examples)";
        }
        return "## Examples\n" + String.join("\n", lines);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractOpenAiMessageContent(String rawResponse) {
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

    private String extractGeminiMessageContent(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        JsonNode root = parseJsonObject(rawResponse);
        if (root == null || !root.has("candidates") || !root.get("candidates").isArray() || root.get("candidates").isEmpty()) {
            return null;
        }

        JsonNode first = root.get("candidates").get(0);
        if (first == null || !first.has("content") || !first.get("content").has("parts")) {
            return null;
        }

        JsonNode parts = first.get("content").get("parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part != null && part.has("text") && !part.get("text").isNull()) {
                sb.append(part.get("text").asText());
            }
        }
        String content = sb.toString().trim();
        return content.isEmpty() ? null : content;
    }

    private String extractClaudeMessageContent(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        JsonNode root = parseJsonObject(rawResponse);
        if (root == null || !root.has("content") || !root.get("content").isArray() || root.get("content").isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode part : root.get("content")) {
            if (part != null && part.has("type") && "text".equals(part.get("type").asText())
                    && part.has("text") && !part.get("text").isNull()) {
                sb.append(part.get("text").asText());
            }
        }
        String content = sb.toString().trim();
        return content.isEmpty() ? null : content;
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
            if (codes.size() == categories.size()) {
                List<Pair> aiPairs = pairCodesAndNames(codes, categories);
                for (Pair pair : aiPairs) {
                    Pair matched = matchCandidatePair(pair.code(), pair.name(), contexts);
                    if (matched != null) {
                        resolved.add(matched);
                    }
                }
            } else {
                for (String code : codes) {
                    Pair matched = matchCandidatePair(code, null, contexts);
                    if (matched != null) {
                        resolved.add(matched);
                    }
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

    private ParseOutcome parseClassificationResult(String messageContent, List<Map<String, String>> allContexts) {
        if (messageContent == null || messageContent.isBlank()) {
            return new ParseOutcome(null, "empty_message_content");
        }

        JsonNode json = parseJsonObject(messageContent);
        if (json == null) {
            return new ParseOutcome(null, "json_parse_failed");
        }

        String code = asText(json, "code");
        String category = asText(json, "category");
        String reason = asText(json, "reason");

        if (code == null && category == null) {
            return new ParseOutcome(null, "missing_code_and_category");
        }

        List<String> codeValues = extractCodes(code);
        if (codeValues.isEmpty()) {
            codeValues = extractCodes(category);
        }
        List<String> categoryValues = splitMultiValue(category);
        if (codeValues.isEmpty() && categoryValues.isEmpty()) {
            return new ParseOutcome(null, "no_extractable_code_or_category_values");
        }

        List<Pair> pairs = resolvePairs(codeValues, categoryValues, allContexts);
        if (pairs.isEmpty()) {
            return new ParseOutcome(null, "no_context_pairs_matched");
        }

        String normalizedCodes = joinDistinct(pairs.stream().map(Pair::code).toList());
        String normalizedCategories = joinDistinct(pairs.stream().map(Pair::name).toList());
        if (normalizedCodes == null || normalizedCategories == null) {
            return new ParseOutcome(null, "normalized_values_empty");
        }

        return new ParseOutcome(new AiFallbackResult(normalizedCodes, normalizedCategories, reason), null);
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    private record Pair(String code, String name) {
    }

    private record ParseOutcome(AiFallbackResult result, String failureReason) {
    }

    private record ResolvedAiConfig(
            AiProvider provider,
            String url,
            String modelName,
            Double temperature,
            String apiKey
    ) {
    }
}
