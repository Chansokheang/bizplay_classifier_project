package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A persisted row of conversational_cost_expense (a COST or ETC expense line). */
@Getter
@Setter
@NoArgsConstructor
public class CostExpense {
    private UUID id;
    /** "COST" or "ETC". */
    private String expenseType;
    private String taxCode;
    private String category;
    private String usePurpose;
    private String account;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate evidenceDate;
    private String description;
    private BigDecimal policyAmount;
    private BigDecimal applicationAmount;
    private String excessReason;
    private String note;
}
