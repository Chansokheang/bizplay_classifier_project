package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A persisted row of conversational_transportation_expense (a TRANSPORTATION expense line). */
@Getter
@Setter
@NoArgsConstructor
public class TransportationExpense {
    private UUID id;
    private String taxCode;
    private String category;
    private String usePurpose;
    private String account;
    private String transportationMethod;
    private String originLocation;
    private String destinationLocation;
    private LocalDate usageDate;
    private String vendor;
    private BigDecimal supplyPrice;
    private BigDecimal tax;
    private BigDecimal amountUsed;
    private BigDecimal regulatedAmount;
}
