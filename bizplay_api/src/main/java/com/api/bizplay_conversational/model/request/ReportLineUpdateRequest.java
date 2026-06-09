package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Full replace of one expense-report line (conversational_trip_report row + its linked expense).
 * The expense fields use the same shape as a create detail. Omitted detail fields are reset to
 * defaults (this is a PUT/replace, not a partial patch).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportLineUpdateRequest {

    /** Section of the line. Optional — keeps the existing section when omitted. */
    @JsonProperty("SectionCode")
    @JsonAlias({"sectionCode", "section_code", "Section", "section"})
    @Schema(example = "TRANSPORTATION", description = "COST, TRANSPORTATION, or ETC")
    @Size(max = 30)
    private String sectionCode;

    /** Optional approval_status; keeps the existing value when omitted. */
    @JsonProperty("approvalStatus")
    @JsonAlias({"approval_status", "ApprovalStatus", "status"})
    @Schema(description = "One of: 'Request for approval', 'Business trip cancellation', 'Approval complete'")
    @Size(max = 50)
    private String approvalStatus;

    /** The expense line's fields (same shape as a create detail). */
    @JsonProperty("Detail")
    @JsonAlias({"detail"})
    @Valid
    private ExpenseDetailRequest detail;
}
