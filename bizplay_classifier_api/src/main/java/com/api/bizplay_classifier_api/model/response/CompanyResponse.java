package com.api.bizplay_classifier_api.model.response;

import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.enums.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyResponse {
    private UUID companyId;
    private String companyName;
    private String businessNumber;
    private CompanyType types;
    private List<RuleDTO> ruleDTOList;
}
