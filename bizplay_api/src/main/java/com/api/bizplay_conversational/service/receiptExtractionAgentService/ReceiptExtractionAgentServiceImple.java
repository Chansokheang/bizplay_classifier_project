package com.api.bizplay_conversational.service.receiptExtractionAgentService;

import com.api.bizplay_conversational.model.response.ReceiptExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemma-backed implementation. Extracts PDF text with PDFBox, then asks Gemma to read off the raw
 * receipt fields as JSON. Gemma is not a thinking model, so {@code stripThink} is effectively a
 * no-op, but it is applied defensively to stay consistent with the other sub-agents.
 */
@Slf4j
@Service
public class ReceiptExtractionAgentServiceImple implements ReceiptExtractionAgentService {

    /** Guardrail: cap how much extracted text we feed the model. */
    private static final int MAX_TEXT_CHARS = 12_000;

    private static final String SYSTEM_PROMPT = """
            You read ONE expense receipt and return ONLY its literal facts as JSON. No prose, no markdown, no code fences.
            Shape:
            {
              "documentType": "hotel|flight|train|taxi|bus|restaurant|other",
              "vendor": "...",
              "currency": "KRW",
              "date": "YYYY-MM-DD",
              "startDate": "YYYY-MM-DD",
              "endDate": "YYYY-MM-DD",
              "origin": "...",
              "destination": "...",
              "originCity": "...",
              "originCountry": "...",
              "destinationCity": "...",
              "destinationCountry": "...",
              "transportationMethod": "...",
              "totalAmount": 0,
              "supplyPrice": 0,
              "tax": 0,
              "approvalNumber": "..."
            }
            Rules:
            - Report ONLY what is printed on the receipt. Use null for anything not present. Do NOT guess.
            - origin/destination/transportationMethod apply only to transport tickets; leave null otherwise.
            - origin/destination are the literal place text as printed (airport code, station, or city).
            - originCity/originCountry/destinationCity/destinationCountry: ONLY for transport tickets,
              resolve the printed place to its city and country using common geographic knowledge
              (e.g. airport code "PNH" or "Phnom Penh Intl" -> city "Phnom Penh", country "Cambodia";
              "ICN"/"Incheon" -> city "Seoul", country "South Korea"). Use the English city/country name.
              Leave null if the place is genuinely unknown. This is the ONLY place you may use outside
              knowledge; every other field must be copied literally from the receipt.
            - startDate/endDate apply to stays (lodging); leave null otherwise.
            - Amounts are plain numbers (no currency symbols, no thousands separators).
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.receipt-extraction-agent.model:gemma-4-e4b}")
    private String modelName;

    public ReceiptExtractionAgentServiceImple(Map<String, ChatClient> chatClientRegistry, ObjectMapper objectMapper,
                                              LlmSettingsService llmSettingsService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReceiptExtractionResult extract(String corpNo, byte[] content, String filename) {
        String text = extractText(content, filename);
        if (text == null || text.isBlank()) {
            log.warn("Receipt '{}' produced no extractable text (image-only/scanned PDFs need OCR, not supported).", filename);
            return emptyResult();
        }
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }

        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            log.warn("Receipt extraction model is not configured: {}", modelName);
            return emptyResult();
        }

        try {
            List<org.springframework.ai.chat.messages.Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(SYSTEM_PROMPT));
            prompt.add(new UserMessage("Receipt"
                    + (filename != null ? " (" + filename + ")" : "") + ":\n" + text));

            String raw = client.prompt().messages(prompt).call().content();
            log.info("Receipt extraction raw output ({}): {}", filename, raw);

            String json = extractJson(stripThink(raw));
            if (json == null) {
                return emptyResult();
            }
            ReceiptExtractionResult result = objectMapper.readValue(json, ReceiptExtractionResult.class);
            if (result == null) {
                return emptyResult();
            }
            result.setHasContent(true);
            return result;
        } catch (Exception e) {
            log.warn("Receipt extraction failed for '{}': {}", filename, e.getMessage());
            return emptyResult();
        }
    }

    private String extractText(byte[] content, String filename) {
        if (content == null || content.length == 0) {
            return null;
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("Failed to read receipt PDF '{}': {}", filename, e.getMessage());
            return null;
        }
    }

    private ReceiptExtractionResult emptyResult() {
        ReceiptExtractionResult r = new ReceiptExtractionResult();
        r.setHasContent(false);
        return r;
    }

    /** Remove reasoning blocks emitted by thinking models (no-op for Gemma; kept for consistency). */
    private String stripThink(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)</?think>", "");
        return cleaned.trim();
    }

    /** Extract the first JSON object from the model output, tolerating stray text or fences. */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }
}
