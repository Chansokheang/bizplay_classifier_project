package com.api.bizplay_conversational.service.staffService;

import com.api.bizplay_conversational.model.response.StaffLookupResult;

public interface StaffService {
    StaffLookupResult lookup(String corpNo, String name);
}
