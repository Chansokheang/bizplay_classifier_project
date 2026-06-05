package com.api.bizplay_conversational.service.requiredFieldValidationService;

import com.api.bizplay_conversational.model.entity.TravelerDraft;
import com.api.bizplay_conversational.model.entity.TripInformationDraft;
import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.MissingFieldsResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic required-field check for a trip-plan draft.
 *
 * <p>Required to move a draft to review:
 * <ul>
 *   <li>Trip level: destination, travel dates (start+end, or a free-text period), purpose,
 *       and at least one traveler.</li>
 *   <li>Per traveler: an origin (where they depart from). A traveler's destination may be inherited
 *       from the trip-level destination, so it is only flagged when neither is set.</li>
 * </ul>
 * Return point and transportation method are treated as optional.
 */
@Slf4j
@Service
public class RequiredFieldValidationServiceImple implements RequiredFieldValidationService {

    @Override
    public MissingFieldsResult validate(TripPlanDraft draft) {
        List<String> missing = new ArrayList<>();

        if (draft == null || !isPresent(draft.getPlanType())) {
            missing.add("plan type");
        }

        TripInformationDraft trip = draft == null ? null : draft.getTripInformation();

        boolean tripDestinationPresent = trip != null && isPresent(trip.getDestination());
        if (!tripDestinationPresent) {
            missing.add("trip destination");
        }
        if (trip == null || !hasTravelDates(trip)) {
            missing.add("travel dates");
        }
        // Purpose holds the business-trip type (General / Educational / Overseas business trip).
        if (trip == null || !isPresent(trip.getPurpose())) {
            missing.add("business-trip type (general/educational/overseas)");
        }

        List<TravelerDraft> travelers = trip == null ? null : trip.getTravelers();
        if (travelers == null || travelers.isEmpty()) {
            missing.add("at least one traveler");
        } else {
            for (TravelerDraft t : travelers) {
                String who = isPresent(t.getName()) ? t.getName().trim() : "a traveler";
                if (!isPresent(t.getOrigin())) {
                    missing.add("origin for " + who);
                }
                // Destination can come from the trip level; only flag if neither is set.
                if (!isPresent(t.getDestination()) && !tripDestinationPresent) {
                    missing.add("destination for " + who);
                }
            }
        }

        return MissingFieldsResult.builder()
                .complete(missing.isEmpty())
                .missing(missing)
                .build();
    }

    /** Dates are satisfied by an explicit start+end, or by a free-text business period. */
    private boolean hasTravelDates(TripInformationDraft trip) {
        boolean explicit = trip.getBusinessStartDate() != null && trip.getBusinessEndDate() != null;
        return explicit || isPresent(trip.getBusinessPeriod());
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
