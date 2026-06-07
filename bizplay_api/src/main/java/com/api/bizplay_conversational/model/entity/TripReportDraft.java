package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "CorpNo", "PlanType", "Attachemnt", "TripInformation",
        "CostInformation", "TransportationInformation", "Etc",
        "TripPlanId", "agentSessionId", "missingFields"
})
public class TripReportDraft {

    private UUID agentSessionId;

    /** The trip plan this report is for (conversational_trip_plan.id). Internal link, set at bootstrap. */
    @JsonProperty("TripPlanId")
    private UUID tripPlanId;

    @JsonProperty("CorpNo")
    private String corpNo;

    @JsonProperty("PlanType")
    private String planType;

    @JsonProperty("Attachemnt")
    @JsonAlias({"Attachment", "Attachments"})
    private List<AttachmentDraft> attachments = new ArrayList<>();

    @JsonProperty("TripInformation")
    private TripInformationDraft tripInformation = new TripInformationDraft();

    @JsonProperty("CostInformation")
    private ExpenseSectionDraft costInformation = new ExpenseSectionDraft();

    @JsonProperty("TransportationInformation")
    private ExpenseSectionDraft transportationInformation = new ExpenseSectionDraft();

    @JsonProperty("Etc")
    private ExpenseSectionDraft etc = new ExpenseSectionDraft();

    private List<String> missingFields = new ArrayList<>();
}
