package com.api.bizplay_conversational.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of resolving extracted traveler names against the staff DB during a merge.
 * Matched (unique) travelers are added to the draft directly; this carries back the names that
 * could NOT be added so the agent can tell the user about them.
 */
@Getter
@NoArgsConstructor
public class TravelerResolution {

    /** Names with no staff match at all. */
    private final List<String> notFound = new ArrayList<>();
    /** Names that matched several staff (duplicate names) — held pending the user's pick. */
    private final List<Ambiguous> ambiguous = new ArrayList<>();

    public boolean isEmpty() {
        return notFound.isEmpty() && ambiguous.isEmpty();
    }

    public void merge(TravelerResolution other) {
        if (other != null) {
            notFound.addAll(other.notFound);
            ambiguous.addAll(other.ambiguous);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Ambiguous {
        private final String name;
        private final List<StaffLookupResult> candidates;
    }
}
