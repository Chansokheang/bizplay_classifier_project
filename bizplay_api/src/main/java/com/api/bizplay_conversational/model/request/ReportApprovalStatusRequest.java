package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Update only the approval_status of an expense-report line, identified by its id. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportApprovalStatusRequest {

    @JsonProperty("id")
    @JsonAlias({"reportId", "report_id", "Id", "ReportId"})
    @Schema(description = "conversational_trip_report.id")
    @NotBlank
    @Size(max = 36)
    private String id;

    @JsonProperty("approvalStatus")
    @JsonAlias({"approval_status", "ApprovalStatus", "status", "Status"})
    @Schema(example = "Approval complete",
            description = "One of: 'Request for approval', 'Business trip cancellation', 'Approval complete'")
    @NotBlank
    @Size(max = 50)
    private String approvalStatus;
}
