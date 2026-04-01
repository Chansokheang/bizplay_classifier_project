package com.api.bizplay_classifier_api.service.ruleService;

import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.RuleUpdateRequest;
import com.api.bizplay_classifier_api.model.response.DataTrainSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface RuleService {
    RuleDTO createRule(RuleRequest ruleRequest);

    RuleDTO updateRuleByRuleId(UUID ruleId, RuleUpdateRequest ruleUpdateRequest);

    List<RuleDTO> getAllRulesByCompanyId(UUID companyId);

    DataTrainSummaryResponse trainRulesFromExcel(MultipartFile file, UUID companyId, String sheetName);
}
