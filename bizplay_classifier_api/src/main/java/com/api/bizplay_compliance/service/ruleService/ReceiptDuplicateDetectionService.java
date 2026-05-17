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
            String messageContent = executePrompt(buildReceiptRequestBody(
                    receiptNumber,
                    receiptFile.getContentType(),
                    receiptFile.getBytes()
            ));
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

    public ReceiptComparisonResult compareReceiptNumber(String receiptNumber, ReceiptFileRegistryService.ReceiptFileContent receiptFile) {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Receipt number is required.");
        }
        if (receiptFile == null || receiptFile.bytes() == null || receiptFile.bytes().length == 0) {
            throw new IllegalArgumentException("Receipt file is required.");
        }

        String messageContent = executePrompt(buildReceiptRequestBody(
                receiptNumber,
                receiptFile.contentType(),
                receiptFile.bytes()
        ));
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
    }

    public CardComparisonResult compareCardNumber(String cardNumber, MultipartFile receiptFile) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number is required.");
        }
        if (receiptFile == null || receiptFile.isEmpty()) {
            throw new IllegalArgumentException("Receipt file is required.");
        }

        try {
            String messageContent = executePrompt(buildCardRequestBody(
                    cardNumber,
                    receiptFile.getContentType(),
                    receiptFile.getBytes()
            ));
            if (messageContent == null || messageContent.isBlank()) {
                throw new IllegalStateException("Card comparison AI returned empty content.");
            }

            CardAiResponse aiResponse = parseCardAiResponse(messageContent);
            String inputCardDigits = normalizeDigits(cardNumber);
            String detectedPattern = resolveTrustedCardPattern(aiResponse);
            CardPatternAnalysis patternAnalysis = analyzeCardPattern(inputCardDigits, detectedPattern);

            return new CardComparisonResult(
                    cardNumber,
                    detectedPattern,
                    aiResponse.detectedCardLine(),
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

    public CardComparisonResult compareCardNumber(String cardNumber, ReceiptFileRegistryService.ReceiptFileContent receiptFile) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number is required.");
        }
        if (receiptFile == null || receiptFile.bytes() == null || receiptFile.bytes().length == 0) {
            throw new IllegalArgumentException("Receipt file is required.");
        }

        String messageContent = executePrompt(buildCardRequestBody(
                cardNumber,
                receiptFile.contentType(),
                receiptFile.bytes()
        ));
        if (messageContent == null || messageContent.isBlank()) {
            throw new IllegalStateException("Card comparison AI returned empty content.");
        }

        CardAiResponse aiResponse = parseCardAiResponse(messageContent);
        String inputCardDigits = normalizeDigits(cardNumber);
        String detectedPattern = resolveTrustedCardPattern(aiResponse);
        CardPatternAnalysis patternAnalysis = analyzeCardPattern(inputCardDigits, detectedPattern);

        return new CardComparisonResult(
                cardNumber,
                detectedPattern,
                aiResponse.detectedCardLine(),
                aiResponse.detectedFieldLabel(),
                detectedPattern,
                patternAnalysis.visibleDigitsFound(),
                patternAnalysis.matches(),
                aiResponse.confidence(),
                aiResponse.reason()
        );
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

    private Map<String, Object> buildReceiptRequestBody(String receiptNumber, String contentType, byte[] fileBytes) {
        String prompt = """
                Read the uploaded receipt image and extract exactly one receipt identifier number.

                Return exactly one JSON object and nothing else:
                {
                  "detectedReceiptNumber": "string",
                  "detectedFieldLabel": "string",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "short explanation"
                }

                Rules:
                - Choose only one receipt number. Do not return multiple candidates.
                - Use document meaning, not a fixed rule order, to decide which single number is the true receipt identifier.
                - Consider labels such as "승인번호", "전표번호", "일련번호", "거래번호", "매출전표번호", "영수증번호", "승인", or similar labels, but choose the one that best functions as the actual receipt or transaction identifier on that specific receipt.
                - Prefer the number that represents the transaction or receipt itself, not merchant number, business number, terminal number, phone number, card number, date, or amount.
                - If several labeled numbers exist, think about which one is most likely the real receipt identifier in context and return only that one.
                - Do not infer, correct, or rewrite digits. Return only digits that are actually visible in the receipt image.
                - Do not use any external expected value. Base the answer only on the image content.
                - If there is no explicit label, do not guess from unrelated printed numbers unless it is the only strong receipt identifier candidate.
                - Set detectedFieldLabel to the label text you used. If there is no label, use "INFERRED". If nothing reliable exists, use an empty string.
                - If multiple candidate numbers exist and none is clearly best, set detectedReceiptNumber to an empty string and confidence to LOW.
                - If the number cannot be read confidently, set detectedReceiptNumber to an empty string.
                - Do not include markdown or extra prose.
                """;

        return buildImageRequestBody(prompt, contentType, fileBytes);
    }

    private Map<String, Object> buildCardRequestBody(String cardNumber, String contentType, byte[] fileBytes) {
        String prompt = """
                Read the uploaded receipt image and extract the card number pattern shown on the receipt.

                Return exactly one JSON object and nothing else:
                {
                  "detectedCardPattern": "string",
                  "detectedCardLine": "string",
                  "detectedFieldLabel": "string",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "short explanation"
                }

                Rules:
                - Find the line labeled "카드번호" or the closest equivalent card-number label.
                - Set detectedCardLine to that exact line.
                - Set detectedCardPattern to only the value on that same line.
                - Keep visible digits and mask characters exactly as shown, with only minor separator or spacing normalization allowed.
                - If the line shows both a visible prefix and suffix, include both. Do not shorten to only the last 4 digits.
                - Do not mix in digits from phone numbers, business numbers, approval numbers, serial numbers, voucher numbers, dates, or amounts.
                - Do not infer, correct, or guess hidden digits.
                - If no reliable card-number line is visible, return an empty detectedCardPattern and LOW confidence.
                - Do not include markdown or extra prose.
                """;

        return buildImageRequestBody(prompt, contentType, fileBytes);
    }

    private Map<String, Object> buildImageRequestBody(String prompt, String contentType, byte[] fileBytes) {
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);

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
                readText(root, "detectedCardLine"),
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

    private String resolveTrustedCardPattern(CardAiResponse aiResponse) {
        String llmPattern = normalizeCardPattern(aiResponse.detectedCardPattern());
        if (isCardFieldLabel(aiResponse.detectedFieldLabel()) && looksLikeCardPattern(llmPattern)) {
            return llmPattern;
        }

        String linePattern = extractCardPatternFromLine(aiResponse.detectedCardLine());
        if (!linePattern.isBlank()) {
            return linePattern;
        }

        return "";
    }

    private boolean isCardFieldLabel(String fieldLabel) {
        if (fieldLabel == null || fieldLabel.isBlank()) {
            return false;
        }

        String normalized = fieldLabel.replaceAll("\\s+", "");
        return normalized.contains("카드번호") || normalized.contains("카드");
    }

    private String extractCardPatternFromLine(String detectedCardLine) {
        if (detectedCardLine == null || detectedCardLine.isBlank()) {
            return "";
        }

        StringBuilder currentToken = new StringBuilder();
        String bestToken = "";

        for (char character : detectedCardLine.toCharArray()) {
            if (Character.isDigit(character)
                    || character == '*'
                    || character == 'x'
                    || character == 'X'
                    || character == '#'
                    || character == '-'
                    || Character.isWhitespace(character)) {
                currentToken.append(character);
                continue;
            }

            bestToken = chooseBetterCardToken(bestToken, currentToken.toString());
            currentToken.setLength(0);
        }

        bestToken = chooseBetterCardToken(bestToken, currentToken.toString());
        return normalizeCardPattern(bestToken);
    }

    private String chooseBetterCardToken(String currentBest, String candidate) {
        String normalizedCandidate = normalizeCardPattern(candidate);
        if (!looksLikeCardPattern(normalizedCandidate)) {
            return currentBest;
        }

        String normalizedBest = normalizeCardPattern(currentBest);
        if (!looksLikeCardPattern(normalizedBest) || normalizedCandidate.length() > normalizedBest.length()) {
            return candidate;
        }

        return currentBest;
    }

    private boolean looksLikeCardPattern(String normalizedPattern) {
        if (normalizedPattern.isBlank()) {
            return false;
        }

        int digitCount = 0;
        int maskCount = 0;
        int visibleChunkCount = 0;
        boolean inVisibleChunk = false;
        for (char character : normalizedPattern.toCharArray()) {
            if (Character.isDigit(character)) {
                digitCount++;
                if (!inVisibleChunk) {
                    visibleChunkCount++;
                    inVisibleChunk = true;
                }
            } else if (character == '*') {
                maskCount++;
                inVisibleChunk = false;
            } else {
                inVisibleChunk = false;
            }
        }

        if (digitCount < 4 || (digitCount + maskCount) < 8) {
            return false;
        }

        if (visibleChunkCount >= 2) {
            return true;
        }

        return digitCount >= 6 && !normalizedPattern.startsWith("*");
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
            String detectedCardLine,
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
            String detectedCardLine,
            String detectedFieldLabel,
            String normalizedDetectedCardPattern,
            boolean visibleDigitsFound,
            boolean matches,
            String confidence,
            String reason
    ) {
    }
}
