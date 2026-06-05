package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TripInformationDraft {

    @JsonProperty("Purpose")
    private String purpose;

    @JsonProperty("BusinessPeriod")
    private String businessPeriod;

    @JsonProperty("BusinessStartDate")
    private LocalDate businessStartDate;

    @JsonProperty("BusinessEndDate")
    private LocalDate businessEndDate;

    @JsonProperty("Destination")
    private String destination;

    @JsonProperty("Title")
    private String title;

    @JsonProperty("Content")
    private String content;

    @JsonProperty("Business Trip Classifcation")
    @JsonAlias({"Business Trip Classification", "BusinessTripClassification"})
    private String businessTripClassification;

    @JsonProperty("Travelers")
    private List<TravelerDraft> travelers = new ArrayList<>();
}
