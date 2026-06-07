package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Update only the approval_status of a trip plan, identified by its id. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanApprovalStatusRequest {

    @JsonProperty("id")
    @JsonAlias({"planId", "plan_id", "Id", "PlanId"})
    @Schema(example = "d42daf38-24cc-46db-9506-e4cd0df952bc", description = "conversational_trip_plan.id")
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
