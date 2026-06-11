package com.api.bizplay_conversational.service.staffService;

import com.api.bizplay_conversational.model.request.StaffRequest;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.model.response.StaffResponse;

import java.util.List;

public interface StaffService {

    /** Resolve a name to a single best-match staff member (first hit). */
    StaffLookupResult lookup(String corpNo, String name);

    /**
     * All staff members that match a name (deduped). Empty = not found; size 1 = unique;
     * size &gt; 1 = ambiguous (duplicate names) and needs user disambiguation.
     */
    List<StaffLookupResult> findCandidates(String corpNo, String name);

    // --- CRUD management ---------------------------------------------------------

    StaffResponse create(StaffRequest request);

    List<StaffResponse> getByCorpNo(String corpNo);

    StaffResponse getById(String id);

    StaffResponse update(String id, StaffRequest request);

    void deleteById(String id);
}
