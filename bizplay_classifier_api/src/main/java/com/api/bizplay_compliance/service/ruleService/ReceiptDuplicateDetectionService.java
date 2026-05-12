package com.api.bizplay_compliance.service.ruleService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReceiptDuplicateDetectionService {

    private static final String DEFAULT_MODEL = "gemma-4-E4B-8b-instruct";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String gemmaUrl;
    private final String apiKey;
    private final String modelName;

    public ReceiptDuplicateDetectionService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.compliance.receipt-compare.url:http://gpu-local.sovanreach.com:9020/api/v1/gemma-4-E4B-8b-instruct/chat/completions}") String gemmaUrl,
            @Value("${app.compliance.receipt-compare.api-key:sk-d7a20eb034c847e8994e192b40c69a61}") String apiKey,
            @Value("${app.compliance.receipt-compare.model:" + DEFAULT_MODEL + "}") String modelName,
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

    public ReceiptComparisonResult compareReceiptNumber(String receiptNumber, MultipartFile receiptFile) {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Receipt number is required.");
        }
        if (receiptFile == null || receiptFile.isEmpty()) {
            throw new IllegalArgumentException("Receipt file is required.");
        }

        try {
            String messageContent = executePrompt(buildReceiptRequestBody(receiptNumber, receiptFile));
            if (messageContent == null || messageContent.isBlank()) {
                throw new IllegalStateException("Receipt comparison AI returned empty content.");
            }

            ReceiptAiResponse aiResponse = parseReceiptAiResponse(messageContent);
            String expectedNormalized = normalizeAlphaNumeric(receiptNumber);
            String detectedNormalized = normalizeAlphaNumeric(aiResponse.detectedReceiptNumber());
            boolean numberDetected = !detectedNormalized.isBlank();
            boolean matches = !expectedNormalized.isBlank()
                    && numberDetected
                    && expectedNormalized.equals(detectedNormalized);

            return new ReceiptComparisonResult(
                    receiptNumber,
                    aiResponse.detectedReceiptNumber(),
                    aiResponse.detectedFieldLabel(),
                    expectedNormalized,
                    detectedNormalized,
                    numberDetected,
                    matches,
                    aiResponse.confidence(),
                    aiResponse.reason()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read receipt file.", exception);
        }
    }

    public CardComparisonResult compareCardNumber(String cardNumber, MultipartFile receiptFile) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number is required.");
        }
        if (receiptFile == null || receiptFile.isEmpty()) {
            throw new IllegalArgumentException("Receipt file is required.");
        }

        try {
            String messageContent = executePrompt(buildCardRequestBody(cardNumber, receiptFile));
            if (messageContent == null || messageContent.isBlank()) {
                throw new IllegalStateException("Card comparison AI returned empty content.");
            }

            CardAiResponse aiResponse = parseCardAiResponse(messageContent);
            String inputCardDigits = normalizeDigits(cardNumber);
            String detectedPattern = normalizeCardPattern(aiResponse.detectedCardPattern());
            CardPatternAnalysis patternAnalysis = analyzeCardPattern(inputCardDigits, detectedPattern);

            return new CardComparisonResult(
                    cardNumber,
                    aiResponse.detectedCardPattern(),
                    aiResponse.detectedFieldLabel(),
                    detectedPattern,
                    patternAnalysis.visibleDigitsFound(),
                    patternAnalysis.matches(),
                    aiResponse.confidence(),
                    aiResponse.reason()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read receipt file.", exception);
        }
    }

    private String executePrompt(Map<String, Object> requestBody) {
        byte[] rawResponseBytes = restClient.post()
                .uri(gemmaUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                .headers(headers -> headers.set("x-api-key", apiKey))
                .body(requestBody)
                .retrieve()
                .body(byte[].class);

        return extractMessageContent(decodeResponseBody(rawResponseBytes));
    }

    private Map<String, Object> buildReceiptRequestBody(String receiptNumber, MultipartFile receiptFile) throws IOException {
        String prompt = """
                Read the uploaded receipt image and extract the most appropriate receipt identifier number.

                Compare the detected receipt number to the expected receipt number: %s

                Return exactly one JSON object and nothing else:
                {
                  "detectedReceiptNumber": "string",
                  "detectedFieldLabel": "string",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "short explanation"
                }

                Rules:
                - Look for Korean receipt identifier labels such as "승인번호", "전표번호", "일련번호", "거래번호", "매출전표번호", "영수증번호", "승인", or similar labels.
                - If a clearly labeled identifier exists, prefer it over card number, merchant number, terminal number, business number, phone number, date, or amount.
                - If there is no explicit label, choose the number that most likely acts as the receipt or approval identifier for card settlement.
                - Set detectedFieldLabel to the label text you used. If there is no label, use "INFERRED". If nothing reliable exists, use an empty string.
                - If multiple candidate numbers exist and none is clearly best, set detectedReceiptNumber to an empty string and confidence to LOW.
                - If the number cannot be read confidently, set detectedReceiptNumber to an empty string.
                - Do not include markdown or extra prose.
                """.formatted(receiptNumber);

        return buildImageRequestBody(prompt, receiptFile);
    }

    private Map<String, Object> buildCardRequestBody(String cardNumber, MultipartFile receiptFile) throws IOException {
        String prompt = """
                Read the uploaded receipt image and extract the card number pattern shown on the receipt.

                Compare the detected card pattern to this expected card number: %s

                Return exactly one JSON object and nothing else:
                {
                  "detectedCardPattern": "string",
                  "detectedFieldLabel": "string",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "short explanation"
                }

                Rules:
                - Look for labels such as "카드번호", "카드 번호", or similar card-number labels.
                - Receipts often mask middle digits using characters like "*", "x", or "#". Keep that masking pattern in detectedCardPattern.
                - Prefer the card number shown on the receipt over approval number, receipt number, merchant number, phone number, business number, date, or amount.
                - If no card number is visible, set detectedCardPattern to an empty string and confidence to LOW.
                - If a masked card number is visible, return the masked value exactly as seen except you may normalize whitespace and separator characters.
                - Do not guess digits hidden behind masks.
                - Do not include markdown or extra prose.
                """.formatted(cardNumber);

        return buildImageRequestBody(prompt, receiptFile);
    }

    private Map<String, Object> buildImageRequestBody(String prompt, MultipartFile receiptFile) throws IOException {
        String contentType = receiptFile.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(receiptFile.getBytes());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("temperature", 0.0);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                        )
                )
        ));
        return body;
    }

    private ReceiptAiResponse parseReceiptAiResponse(String messageContent) {
        JsonNode root = parseJsonObject(messageContent);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Receipt comparison AI returned invalid JSON.");
        }

        return new ReceiptAiResponse(
                readText(root, "detectedReceiptNumber"),
                readText(root, "detectedFieldLabel"),
                normalizeConfidence(readText(root, "confidence")),
                readText(root, "reason")
        );
    }

    private CardAiResponse parseCardAiResponse(String messageContent) {
        JsonNode root = parseJsonObject(messageContent);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Card comparison AI returned invalid JSON.");
        }

        return new CardAiResponse(
                readText(root, "detectedCardPattern"),
                readText(root, "detectedFieldLabel"),
                normalizeConfidence(readText(root, "confidence")),
                readText(root, "reason")
        );
    }

    private String extractMessageContent(String rawResponse) {
        JsonNode root = parseJsonObject(rawResponse);
        if (root == null || !root.has("choices") || !root.get("choices").isArray() || root.get("choices").isEmpty()) {
            return null;
        }

        JsonNode messageNode = root.get("choices").get(0).path("message").path("content");
        if (messageNode.isTextual()) {
            return messageNode.asText();
        }

        if (messageNode.isArray()) {
            StringBuilder content = new StringBuilder();
            for (JsonNode item : messageNode) {
                JsonNode textNode = item.path("text");
                if (textNode.isTextual()) {
                    content.append(textNode.asText());
                }
            }
            return content.toString().trim();
        }

        return null;
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

    private String decodeResponseBody(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }

        return new String(body, StandardCharsets.UTF_8);
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (!valueNode.isTextual()) {
            return "";
        }
        return valueNode.asText("").trim();
    }

    private String normalizeAlphaNumeric(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
    }

    private String normalizeDigits(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    private String normalizeCardPattern(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.toUpperCase()
                .replace('X', '*')
                .replace('#', '*');

        StringBuilder builder = new StringBuilder();
        for (char character : normalized.toCharArray()) {
            if (Character.isDigit(character) || character == '*') {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private CardPatternAnalysis analyzeCardPattern(String inputCardDigits, String detectedPattern) {
        if (inputCardDigits.isBlank() || detectedPattern.isBlank()) {
            return new CardPatternAnalysis(false, false);
        }

        List<String> visibleChunks = List.of(detectedPattern.split("\\*+"))
                .stream()
                .filter(chunk -> !chunk.isBlank())
                .toList();

        if (visibleChunks.isEmpty()) {
            return new CardPatternAnalysis(false, false);
        }

        int searchStart = 0;
        for (int index = 0; index < visibleChunks.size(); index++) {
            String chunk = visibleChunks.get(index);
            int foundAt = inputCardDigits.indexOf(chunk, searchStart);
            if (foundAt < 0) {
                return new CardPatternAnalysis(true, false);
            }

            if (index == 0 && !detectedPattern.startsWith("*") && foundAt != 0) {
                return new CardPatternAnalysis(true, false);
            }

            searchStart = foundAt + chunk.length();
        }

        if (!detectedPattern.endsWith("*")) {
            String suffix = visibleChunks.getLast();
            if (!inputCardDigits.endsWith(suffix)) {
                return new CardPatternAnalysis(true, false);
            }
        }

        return new CardPatternAnalysis(true, true);
    }

    private String normalizeConfidence(String value) {
        if (value == null || value.isBlank()) {
            return "LOW";
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "LOW";
        };
    }

    private record ReceiptAiResponse(
            String detectedReceiptNumber,
            String detectedFieldLabel,
            String confidence,
            String reason
    ) {
    }

    private record CardAiResponse(
            String detectedCardPattern,
            String detectedFieldLabel,
            String confidence,
            String reason
    ) {
    }

    private record CardPatternAnalysis(
            boolean visibleDigitsFound,
            boolean matches
    ) {
    }

    public record ReceiptComparisonResult(
            String expectedReceiptNumber,
            String detectedReceiptNumber,
            String detectedFieldLabel,
            String normalizedExpectedReceiptNumber,
            String normalizedDetectedReceiptNumber,
            boolean numberDetected,
            boolean matches,
            String confidence,
            String reason
    ) {
    }

    public record CardComparisonResult(
            String expectedCardNumber,
            String detectedCardPattern,
            String detectedFieldLabel,
            String normalizedDetectedCardPattern,
            boolean visibleDigitsFound,
            boolean matches,
            String confidence,
            String reason
    ) {
    }
}
