package com.api.bizplay_compliance.model.request;

public record CardMismatchCheckRequest(
        String cardNumber,
        String receiptCardNumber
) {
}
