package com.api.bizplay_classifier_api.service.ruleService;

import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.RuleUpdateRequest;
import com.api.bizplay_classifier_api.model.request.TrainingDataTrainRequest;
import com.api.bizplay_classifier_api.model.response.DataTrainSummaryResponse;
import com.api.bizplay_classifier_api.model.response.RulePageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
public interface RuleService {
    RuleDTO createRule(RuleRequest ruleRequest);

    RuleDTO updateRuleByRuleId(UUID ruleId, RuleUpdateRequest ruleUpdateRequest);

    void deleteRuleByRuleId(UUID ruleId);

    void deleteRulesByCorpNo(String corpNo);

    RulePageResponse getAllRulesByCompanyId(String companyId, String usageStatus, int page, int limit);

    DataTrainSummaryResponse trainRulesFromExcel(MultipartFile file, String companyId, String sheetName);

    DataTrainSummaryResponse trainRulesFromRequestData(TrainingDataTrainRequest trainingDataTrainRequest);
}
