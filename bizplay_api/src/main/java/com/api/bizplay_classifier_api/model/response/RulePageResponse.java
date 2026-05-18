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
public class RulePageResponse {
    private String corpNo;
    private int page;
    private int limit;
    private int totalRows;
    private List<RuleDTO> items;
}
