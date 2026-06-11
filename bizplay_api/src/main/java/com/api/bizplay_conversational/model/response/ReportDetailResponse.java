package com.api.bizplay_conversational.model.response;

import com.api.bizplay_conversational.model.entity.CostExpense;
import com.api.bizplay_conversational.model.entity.TransportationExpense;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * One expense line of a report (conversational_trip_report_detail) with its linked expense and the
 * source receipt it came from. Exactly one of costExpense / transportationExpense is populated.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportDetailResponse {
    private UUID id;
    /** "COST", "TRANSPORTATION", or "ETC". */
    private String sectionCode;
    /** The source receipt's fileId (from conversational_attachment), if this line came from one. */
    private String attachmentFileId;
    /** Set for COST / ETC lines. */
    private CostExpense costExpense;
    /** Set for TRANSPORTATION lines. */
    private TransportationExpense transportationExpense;
}
