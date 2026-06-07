package com.api.bizplay_conversational.service.receiptExtractionAgentService;

import com.api.bizplay_conversational.model.response.ReceiptExtractionResult;

/**
 * Receipt Extraction Agent (Gemma). First stage of the receipt pipeline: pulls text out of a PDF
 * receipt (Apache PDFBox) and uses Gemma to read off the literal facts (vendor, dates, amounts,
 * tax, route). Data-only — it never touches draft_json and never decides the expense section; the
 * Expense Analysis Agent reconciles and routes afterwards.
 */
public interface ReceiptExtractionAgentService {

    /**
     * Extract raw fields from a single PDF receipt.
     *
     * @param corpNo   tenant (for logging/scope)
     * @param content  raw PDF bytes
     * @param filename original filename (for logging; may be null)
     * @return raw receipt fields; {@code hasContent=false} when the PDF has no usable text
     */
    ReceiptExtractionResult extract(String corpNo, byte[] content, String filename);
}
