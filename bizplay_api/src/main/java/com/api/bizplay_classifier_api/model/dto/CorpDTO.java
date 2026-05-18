package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorpDTO {
    private Long corpId;
    private Long corpGroupId;
    private String corpNo;
    private String corpName;
    private String businessNumber;
    private String corpGroupCode;
    private Timestamp createdDate;
    private List<RuleDTO> ruleDTOList;
}
