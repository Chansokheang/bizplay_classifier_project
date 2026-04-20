package com.api.bizplay_classifier_api.model.response;

import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyResponse {
    private String companyId;
    private String companyName;
    private String businessNumber;
    private List<RuleDTO> ruleDTOList;
}
