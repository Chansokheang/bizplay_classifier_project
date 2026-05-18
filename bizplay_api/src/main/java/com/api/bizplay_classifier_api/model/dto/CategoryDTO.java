package com.api.bizplay_classifier_api.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryDTO {
    private UUID categoryId;
    @JsonProperty("corpNo")
    private String CorpNo;
    private String code;
    private String category;
    private Boolean isUsed;
}
