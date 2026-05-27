package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseDetailRequest {

    @JsonProperty("순번")
    private Integer sequenceNo;

    @JsonProperty("TaxCode")
    @Size(max = 50)
    private String taxCode;

    @JsonProperty("Type")
    @Size(max = 100)
    private String type;

    @JsonProperty("Use")
    @Size(max = 100)
    private String usePurpose;

    @JsonProperty("Account")
    @Size(max = 100)
    private String account;

    @JsonProperty("예산부서")
    @Size(max = 100)
    private String budgetDepartment;

    @JsonProperty("교통수단")
    @Size(max = 100)
    private String transportationMethod;

    @JsonProperty("Origin")
    @Size(max = 255)
    private String origin;

    @JsonProperty("Destination")
    @Size(max = 255)
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

    @JsonProperty("거래처")
    @Size(max = 255)
    private String vendor;

    @JsonProperty("Supply price")
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
    @Size(max = 100)
    private String approvalNumber;
}
