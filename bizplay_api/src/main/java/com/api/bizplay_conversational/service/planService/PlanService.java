package com.api.bizplay_conversational.service.planService;

import com.api.bizplay_conversational.model.request.PlanCreateRequest;
import com.api.bizplay_conversational.model.response.PlanResponse;

public interface PlanService {
    PlanResponse create(PlanCreateRequest request);
}
