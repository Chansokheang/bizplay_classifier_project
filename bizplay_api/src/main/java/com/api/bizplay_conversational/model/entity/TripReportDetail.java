package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A persisted row of conversational_trip_report_detail: one expense line of a report. Links the
 * report header to either a cost or transportation expense (per section_code) and, optionally, to
 * the source receipt it was extracted from. Exactly one of costExpenseId / transportationExpenseId
 * is set, matching section_code (enforced by a DB CHECK constraint).
 */
@Getter
@Setter
@NoArgsConstructor
public class TripReportDetail {
    private UUID id;
    private UUID tripReportId;
    /** "COST", "TRANSPORTATION", or "ETC". */
    private String sectionCode;
    private UUID transportationExpenseId;
    private UUID costExpenseId;
    /** The receipt this line was extracted from (conversational_attachment.id). May be null. */
    private UUID conversationalAttachmentId;
}
