package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseDetail {
    private UUID id;
    private ExpenseSection section;
    private Integer sequenceNo;
    private String taxCode;
    private String type;
    private String usePurpose;
    private String account;
    private Department budgetDepartment;
    private String transportationMethod;
    private String origin;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate usageDate;
    private LocalDate proofDate;
    private String description;
    private String vendor;
    private BigDecimal supplyPrice;
    private BigDecimal tax;
    private BigDecimal amountUsed;
    private BigDecimal regulatedAmount;
    private String applicationAmountReasonForExcess;
    private String briefs;
    private String note;
    private String approvalNumber;
    private LocalDateTime createdAt;
}
