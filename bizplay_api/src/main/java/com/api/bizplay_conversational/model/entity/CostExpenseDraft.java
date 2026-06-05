package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CostExpenseDraft {

    @JsonProperty("ExpenseType")
    private String expenseType;

    @JsonProperty("TaxCode")
    private String taxCode;

    @JsonProperty("Category")
    private String category;

    @JsonProperty("Use")
    private String usePurpose;

    @JsonProperty("Account")
    private String account;

    @JsonProperty("StartDate")
    private LocalDate startDate;

    @JsonProperty("EndDate")
    private LocalDate endDate;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("ProofDate")
    private LocalDate proofDate;

    @JsonProperty("AmountUsed")
    private BigDecimal amountUsed;

    @JsonProperty("RegulatedAmount")
    private BigDecimal regulatedAmount;
}

