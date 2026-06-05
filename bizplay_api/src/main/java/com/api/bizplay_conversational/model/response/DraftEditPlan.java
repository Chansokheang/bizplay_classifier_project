package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-only output of the Draft Update Agent: a validated list of edit operations to apply to the
 * existing trip-plan draft. The agent decides WHAT to change; the RequestBody builder applies the
 * ops (HOW) against a fixed op/field allowlist — the model never writes draft_json directly.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DraftEditPlan {

    private List<Edit> edits = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Edit {
        /**
         * One of: set_trip_field, clear_trip_field, set_traveler_field, clear_traveler_field,
         * set_all_travelers_field, add_traveler, remove_traveler.
         */
        private String op;
        /**
         * Trip field: destination, type, title, start_date, end_date, period, content.
         * Traveler field: origin, destination, return_point, transportation.
         */
        private String field;
        /** Traveler name for traveler-scoped / add / remove ops. */
        private String traveler;
        /** New value for set ops. */
        private String value;
    }
}
