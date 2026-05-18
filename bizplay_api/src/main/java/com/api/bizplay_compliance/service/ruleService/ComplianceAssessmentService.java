package com.api.bizplay_compliance.service.ruleService;

import com.api.bizplay_compliance.model.response.RuleStatusResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComplianceAssessmentService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String gemmaUrl;
    private final String apiKey;
    private final String modelName;

    public ComplianceAssessmentService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.compliance.receipt-compare.url:http://gpu-local.sovanreach.com:9020/api/v1/gemma-4-E4B-8b-instruct/chat/completions}") String gemmaUrl,
            @Value("${app.compliance.receipt-compare.api-key:sk-d7a20eb034c847e8994e192b40c69a61}") String apiKey,
            @Value("${app.compliance.receipt-compare.model:gemma-4-E4B-8b-instruct}") String modelName,
            @Value("${app.compliance.receipt-compare.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.compliance.receipt-compare.read-timeout-ms:60000}") int readTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.gemmaUrl = gemmaUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    public ComplianceAssessment assess(List<RuleStatusResponse> rules) {
        if (rules == null || rules.isEmpty()) {
            return new ComplianceAssessment("NORMAL", "LOW");
        }

        try {
            String content = executePrompt(rules);
            ComplianceAssessment parsed = parseAssessment(content);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
        }

        boolean hasMismatch = rules.stream().anyMatch(rule -> "MISMATCH".equalsIgnoreCase(rule.status()));
        return new ComplianceAssessment(hasMismatch ? "SUSPICION" : "NORMAL", hasMismatch ? "MEDIUM" : "LOW");
    }

    private String executePrompt(List<RuleStatusResponse> rules) {
        byte[] rawResponseBytes = restClient.post()
                .uri(gemmaUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                .headers(headers -> headers.set("x-api-key", apiKey))
                .body(buildRequestBody(rules))
                .retrieve()
                .body(byte[].class);

        return extractMessageContent(new String(rawResponseBytes, StandardCharsets.UTF_8));
    }

    private Map<String, Object> buildRequestBody(List<RuleStatusResponse> rules) {
        String prompt = """
                You are a compliance assessment assistant.

                Based on the 10 rule results below, determine:
                1. complianceStatus: NORMAL or SUSPICION
                2. confidenceLevel: HIGH, MEDIUM, or LOW

                Return exactly one JSON object and nothing else:
                {
                  "complianceStatus": "NORMAL|SUSPICION",
                  "confidenceLevel": "HIGH|MEDIUM|LOW"
                }

                Guidance:
                - If any material rule result is MISMATCH, prefer SUSPICION.
                - If all rules are OK, return NORMAL.
                - Confidence should reflect how strong and consistent the rule results are.

                Rule results JSON:
                %s
                """.formatted(toJson(rules));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("temperature", 0.0);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));
        return body;
    }

    private ComplianceAssessment parseAssessment(String content) {
        JsonNode root = parseJsonObject(content);
        if (root == null || !root.isObject()) {
            return null;
        }

        String complianceStatus = normalizeComplianceStatus(readText(root, "complianceStatus"));
        String confidenceLevel = normalizeConfidenceLevel(readText(root, "confidenceLevel"));
        return new ComplianceAssessment(complianceStatus, confidenceLevel);
    }

    private String extractMessageContent(String rawResponse) {
        JsonNode root = parseJsonObject(rawResponse);
        if (root == null || !root.has("choices") || !root.get("choices").isArray() || root.get("choices").isEmpty()) {
            return null;
        }

        JsonNode contentNode = root.get("choices").get(0).path("message").path("content");
        return contentNode.isTextual() ? contentNode.asText() : null;
    }

    private JsonNode parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(raw.substring(start, end + 1));
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String normalizeComplianceStatus(String value) {
        return "SUSPICION".equalsIgnoreCase(value) ? "SUSPICION" : "NORMAL";
    }

    private String normalizeConfidenceLevel(String value) {
        if ("HIGH".equalsIgnoreCase(value)) {
            return "HIGH";
        }
        if ("MEDIUM".equalsIgnoreCase(value)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    public record ComplianceAssessment(
            String complianceStatus,
            String confidenceLevel
    ) {
    }
}
