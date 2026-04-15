package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditTransactionDTO {
    private UUID id;
    private UUID employeeId;
    private UUID merchantId;
    private LocalDateTime transactionDate;
    private Long amount;
    private String category;
    private String mccCode;
    private String receiptHash;
}
