package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Body for creating an actual Business Trip Report (the "After Business Trip" step). This is the
 * approved conversational report draft_json: it references the trip plan it reports on
 * ({@code TripPlanId}) and carries the expense sections (COST / TRANSPORTATION / ETC) to persist.
 * Trip information itself is NOT re-stored — it already lives on the trip plan.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportCreateRequest {

    @JsonProperty("CorpNo")
    @Schema(example = "1234567890")
    @NotBlank
    @Size(max = 50)
    private String corpNo;

    @JsonProperty("PlanType")
    @Schema(example = "Business Trip Plan")
    @Size(max = 100)
    private String planType;

    /** The conversational session this report was drafted in (conversational_agent_session.id). */
    @JsonProperty("AgentSessionId")
    @JsonAlias({"agentSessionId", "agent_session_id", "SessionId", "sessionId"})
    @Schema(description = "conversational_agent_session.id this report was drafted in")
    @Size(max = 36)
    private String agentSessionId;

    /**
     * The trip plan this report is for (conversational_trip_plan.id). Required, but if omitted it is
     * recovered from the drafting session's draft_json (TripPlanId).
     */
    @JsonProperty("TripPlanId")
    @JsonAlias({"tripPlanId", "trip_plan_id", "PlanId", "planId"})
    @Schema(description = "conversational_trip_plan.id this report reports on")
    @Size(max = 36)
    private String tripPlanId;

    @JsonProperty("Attachemnt")
    @JsonAlias({"Attachment", "Attachments"})
    @Valid
    private List<PlanAttachmentRequest> attachments = new ArrayList<>();

    /** Informational only (the trip data is read from the plan); accepted but not persisted here. */
    @JsonProperty("TripInformation")
    private TripInformationRequest tripInformation;

    @JsonProperty("CostInformation")
    @Valid
    private ExpenseSectionRequest costInformation;

    @JsonProperty("TransportationInformation")
    @Valid
    private ExpenseSectionRequest transportationInformation;

    @JsonProperty("Etc")
    @Valid
    private ExpenseSectionRequest etc;
}
