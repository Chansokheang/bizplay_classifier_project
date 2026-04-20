package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.model.dto.AuditRuleDTO;
import com.api.bizplay_classifier_api.model.dto.AuditTransactionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AIComplianceRepo {
    List<AuditRuleDTO> getActiveAuditRules();

    List<AuditTransactionDTO> findNearbyTransactions(
            @Param("employeeId") UUID employeeId,
            @Param("merchantId") UUID merchantId,
            @Param("transactionId") UUID transactionId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    List<AuditTransactionDTO> findDuplicateReceiptTransactions(
            @Param("receiptHash") String receiptHash,
            @Param("transactionId") UUID transactionId,
            @Param("limit") int limit
    );
}
