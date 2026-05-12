package com.api.bizplay_compliance.model.request;

import java.time.LocalDateTime;

public record NighttimeTransactionCheckRequest(
        LocalDateTime transactionDate
) {
}
