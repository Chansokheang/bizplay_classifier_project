package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Raw, data-only output of the Receipt Extraction Agent (Gemma). It reports only the literal facts
 * it could read off the receipt text; it does NOT decide the expense section, accounting codes, or
 * reconcile against the user's typed details — that is the Expense Analysis Agent's job.
 * All fields are nullable: the agent fills only what the receipt actually contained.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceiptExtractionResult {

    /** Best-effort kind of receipt as written (e.g. "hotel", "flight", "taxi", "restaurant"). */
    private String documentType;

    /** Merchant / vendor / airline name. */
    private String vendor;

    /** ISO currency or symbol as printed (e.g. "KRW"). */
    private String currency;

    /** Primary transaction/proof date (YYYY-MM-DD when parseable). */
    private String date;

    /** Service start/end dates for stays (lodging). YYYY-MM-DD when parseable. */
    private String startDate;
    private String endDate;

    /** Movement details when the receipt is a transport ticket. */
    private String origin;
    private String destination;
    private String transportationMethod;

    /**
     * Normalized city/country for the origin and destination of a transport ticket, resolved by the
     * model from whatever is printed (airport code, station, city). Lets a venue/airport on the
     * receipt be compared geographically against a plan destination. Null for non-transport receipts
     * or when the place can't be resolved.
     */
    private String originCity;
    private String originCountry;
    private String destinationCity;
    private String destinationCountry;

    /** Monetary fields as printed on the receipt. */
    private BigDecimal totalAmount;
    private BigDecimal supplyPrice;
    private BigDecimal tax;

    /** Card/tax approval number if present. */
    private String approvalNumber;

    /** Whether any usable text was extracted at all (false for image-only/scanned PDFs). */
    private boolean hasContent;
}
