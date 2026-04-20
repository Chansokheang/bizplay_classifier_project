package com.api.bizplay_classifier_api.service.aiComplianceService;

import com.api.bizplay_classifier_api.model.dto.AuditRuleResultDTO;
import com.api.bizplay_classifier_api.model.dto.AuditTransactionDTO;

import java.util.List;

public interface AIComplianceService {
    List<AuditRuleResultDTO> runRules(AuditTransactionDTO transaction);
}
