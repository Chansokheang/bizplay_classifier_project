package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TripPlanDraft {

    @JsonProperty("CorpNo")
    private String corpNo;

    @JsonProperty("UserReqId")
    private String userReqId;

    @JsonProperty("PlanType")
    private String planType;

    @JsonProperty("Attachemnt")
    @JsonAlias({"Attachment", "Attachments"})
    private List<AttachmentDraft> attachments = new ArrayList<>();

    @JsonProperty("TripInformation")
    private TripInformationDraft tripInformation = new TripInformationDraft();

    private List<String> missingFields = new ArrayList<>();

    /** Travelers whose name matched several staff; awaiting the user's pick before being added. */
    private List<PendingTravelerDraft> pendingTravelers = new ArrayList<>();

}
