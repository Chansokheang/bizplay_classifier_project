package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanCreateRequest {

    @JsonProperty("CorpNo")
    @Schema(example = "1234567890")
    @NotBlank
    @Size(max = 50)
    private String corpNo;

    @JsonProperty("PlanType")
    @Schema(example = "Business Trip Plan")
    @NotBlank
    @Size(max = 100)
    private String planType;

    /**
     * Optional link back to the conversational session that produced this plan
     * (conversational_agent_session.id — the same value returned as "sessionId" by the trip-plan agent).
     */
    @JsonProperty("AgentSessionId")
    @JsonAlias({"agentSessionId", "agent_session_id", "SessionId", "sessionId"})
    @Schema(example = "c40edac4-2d4b-43b8-97c6-945808d3604d",
            description = "conversational_agent_session.id this plan was created from")
    @Size(max = 36)
    private String agentSessionId;

    @JsonProperty("Attachemnt")
    @JsonAlias({"Attachment", "Attachments"})
    @Valid
    private List<PlanAttachmentRequest> attachments = new ArrayList<>();

    @JsonProperty("TripInformation")
    @NotNull
    @Valid
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
