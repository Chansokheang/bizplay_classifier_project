package com.api.bizplay_conversational.service.pdfAgentService;

import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * PDF sub-agent (twin of the Spreadsheet Agent). Where the Spreadsheet Agent answers "who travels",
 * the PDF Agent answers "where / when / why" by reading trip documents — booking confirmations,
 * invitations, itineraries, approval memos.
 *
 * <p>It extracts text from a (text-based) PDF and reuses the Text Analysis Agent to pull the trip
 * fields, returning data only — it never touches draft_json. Image-only/scanned PDFs (which need
 * OCR) are out of scope for this version.
 */
public interface PdfAgentService {

    /**
     * Extract trip information from a PDF document.
     *
     * @param corpNo   tenant
     * @param content  raw PDF bytes
     * @param filename original filename (for logging; may be null)
     * @param history  prior conversation turns (for context; may be null)
     * @return extracted trip fields, or an empty (category=OTHER) result if the PDF has no usable text
     */
    TextAnalysisResult analyze(String corpNo, byte[] content, String filename, List<Message> history);
}
