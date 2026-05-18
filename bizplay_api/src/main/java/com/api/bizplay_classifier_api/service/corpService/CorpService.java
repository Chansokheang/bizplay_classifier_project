package com.api.bizplay_classifier_api.service.corpService;

import com.api.bizplay_classifier_api.model.request.CorpGroupRequest;
import com.api.bizplay_classifier_api.model.request.CorpRequest;
import com.api.bizplay_classifier_api.model.response.CorpResponse;
import com.api.bizplay_classifier_api.model.response.CorpGroupResponse;

import java.util.List;
public interface CorpService {
    CorpGroupResponse createCorpGroup(CorpGroupRequest corpGroupRequest);

    List<CorpGroupResponse> getAllCorpGroups();

    CorpGroupResponse getCorpGroupByCode(String corpGroupCode);

    List<CorpResponse> getAllCorps();

    List<CorpResponse> getAllCorpsByCorpGroupCode(String corpGroupCode);

    CorpResponse createCorp(CorpRequest corpRequest);

    CorpResponse getCorpByCorpNo(String corpNo);

    void deleteCorpByCorpNo(String corpNo);
}

