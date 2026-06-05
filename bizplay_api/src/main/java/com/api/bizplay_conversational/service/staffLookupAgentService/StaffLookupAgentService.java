package com.api.bizplay_conversational.service.staffLookupAgentService;

import com.api.bizplay_conversational.model.request.StaffLookupAgentRequest;
import com.api.bizplay_conversational.model.response.StaffLookupAgentResponse;

public interface StaffLookupAgentService {
    StaffLookupAgentResponse lookup(StaffLookupAgentRequest request);
}
