package com.api.bizplay_compliance.model.request;

public record LimitExceedCheckRequest(
        Integer amount,
        String category
) {
}
