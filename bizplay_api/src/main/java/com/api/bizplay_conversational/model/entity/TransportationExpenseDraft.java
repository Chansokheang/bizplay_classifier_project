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
public class TransportationExpenseDraft {

    @JsonProperty("TaxCode")
    private String taxCode;

    @JsonProperty("Category")
    private String category;

    @JsonProperty("Use")
    private String usePurpose;

    @JsonProperty("Account")
    private String account;

    @JsonProperty("TransportationMethod")
    private String transportationMethod;

    @JsonProperty("Origin")
    private String origin;

    @JsonProperty("Destination")
    private String destination;

    @JsonProperty("UsageDate")
    private LocalDate usageDate;

    @JsonProperty("Vendor")
    private String vendor;

    @JsonProperty("SupplyPrice")
    private BigDecimal supplyPrice;

    @JsonProperty("Tax")
    private BigDecimal tax;

    @JsonProperty("AmountUsed")
    private BigDecimal amountUsed;

    @JsonProperty("RegulatedAmount")
    private BigDecimal regulatedAmount;
}
