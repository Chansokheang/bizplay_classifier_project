package com.api.bizplay_compliance.model.request;

public record DuplicateReceiptCheckRequest(
        String receiptHash,
        String duplicateReceiptHash
) {
}
