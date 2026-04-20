package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDTO {
    private String companyId;
    private UUID userId;
    private String companyName;
    private String businessNumber;
    private Timestamp createdDate;
    private List<RuleDTO> ruleDTOList;
}
