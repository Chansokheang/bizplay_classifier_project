package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseDetailDraft {

    @JsonProperty("?쒕쾲")
    @JsonAlias("SequenceNo")
    private Integer sequenceNo;

    @JsonProperty("TaxCode")
    private String taxCode;

    @JsonProperty("Category")
    private String category;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Use")
    private String usePurpose;

    @JsonProperty("Account")
    private String account;

    @JsonProperty("?덉궛遺??")
    @JsonAlias("BudgetDepartment")
    private String budgetDepartment;

    @JsonProperty("援먰넻?섎떒")
    @JsonAlias("TransportationMethod")
    private String transportationMethod;

    @JsonProperty("Origin")
    private String origin;

    @JsonProperty("Destination")
    private String destination;

    @JsonProperty("StartDate")
    private LocalDate startDate;

    @JsonProperty("EndDate")
    private LocalDate endDate;

    @JsonProperty("UsageDate")
    private LocalDate usageDate;

    @JsonProperty("ProofDate")
    private LocalDate proofDate;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("嫄곕옒泥?")
    @JsonAlias("Vendor")
    private String vendor;

    @JsonProperty("Supply price")
    @JsonAlias("SupplyPrice")
    private BigDecimal supplyPrice;

    @JsonProperty("Tax")
    private BigDecimal tax;

    @JsonProperty("AmountUsed")
    private BigDecimal amountUsed;

    @JsonProperty("RegulatedAmount")
    private BigDecimal regulatedAmount;

    @JsonProperty("ApplicationAmountReasonForExcess")
    private String applicationAmountReasonForExcess;

    @JsonProperty("Briefs")
    private String briefs;

    @JsonProperty("Note")
    private String note;

    @JsonProperty("ApprovalNumber")
    private String approvalNumber;
}
