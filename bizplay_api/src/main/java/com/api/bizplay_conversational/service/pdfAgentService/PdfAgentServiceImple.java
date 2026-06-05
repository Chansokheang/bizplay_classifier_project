package com.api.bizplay_conversational.service.pdfAgentService;

import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.api.bizplay_conversational.service.textAnalysisAgentService.TextAnalysisAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Extracts text from a PDF (Apache PDFBox) and delegates the field extraction to the Text Analysis
 * Agent, so trip information from a document is parsed exactly like trip information typed in chat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfAgentServiceImple implements PdfAgentService {

    /** Guardrail: cap how much extracted text we feed the model. */
    private static final int MAX_TEXT_CHARS = 12_000;

    private final TextAnalysisAgentService textAnalysisAgentService;

    @Override
    public TextAnalysisResult analyze(String corpNo, byte[] content, String filename, List<Message> history) {
        String text = extractText(content, filename);
        if (text == null || text.isBlank()) {
            log.warn("PDF '{}' produced no extractable text (image-only/scanned PDFs need OCR, not supported).", filename);
            return emptyResult();
        }
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        // Reuse the Text Analysis Agent's extraction + trip-type classification on the PDF text.
        return textAnalysisAgentService.analyze(
                "Trip document" + (filename != null ? " (" + filename + ")" : "") + ":\n" + text,
                history);
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
            log.warn("Failed to read PDF '{}': {}", filename, e.getMessage());
            return null;
        }
    }

    private TextAnalysisResult emptyResult() {
        TextAnalysisResult result = new TextAnalysisResult();
        result.setCategory("OTHER");
        return result;
    }
}
