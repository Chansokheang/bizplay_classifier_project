package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-only output of the Text Analysis Agent. The agent does NOT touch draft_json;
 * it only reports what trip information it could extract from the user's message.
 * The Structured API RequestBody builder is responsible for merging this into draft_json.
 * All fields are nullable: the agent fills only what the NL message actually contained.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextAnalysisResult {

    /** TRANSPORTATION when the message describes trip movement; otherwise OTHER. */
    private String category;

    // Trip-level fields (map to TripInformationDraft when merged)
    private String tripDestination;
    private String businessPeriod;
    private String businessStartDate;
    private String businessEndDate;
    /**
     * The fixed business-trip TYPE, classified into exactly one of:
     * "General business trip" | "Educational business trip" | "Overseas business trip".
     * Maps to TripInformationDraft.Purpose (the 출장목적 dropdown).
     */
    private String tripType;
    /** Free-text description of what the trip is about; maps to TripInformationDraft.Content. */
    private String content;
    /** Legacy/free-text purpose the model may emit; used as a Content fallback only. */
    private String purpose;
    private String title;

    /** Per-person route details extracted from the message. */
    private List<TravelerRoute> travelers = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TravelerRoute {
        private String name;
        private String origin;
        private String destination;
        private String returnPoint;
        private String transportationMethod;
    }
}
