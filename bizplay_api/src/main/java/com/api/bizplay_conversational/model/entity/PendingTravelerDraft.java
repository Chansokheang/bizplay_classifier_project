package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A traveler whose name matched MULTIPLE staff members (duplicate names). Held in the draft until
 * the user picks which candidate they mean; the stored route is then applied to the chosen traveler.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingTravelerDraft {

    /** The name as extracted (e.g. "Vorn Naro"). */
    private String name;

    // The route to apply once the candidate is chosen.
    private String origin;
    private String destination;
    private String returnPoint;
    private String transportationMethod;

    private List<Candidate> candidates = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private UUID staffId;
        private String name;
        private String department;
        private String position;
    }
}
