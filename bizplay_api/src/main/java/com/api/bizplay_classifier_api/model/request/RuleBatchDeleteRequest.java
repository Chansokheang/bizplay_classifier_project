package com.api.bizplay_classifier_api.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class RuleBatchDeleteRequest {

    @NotEmpty(message = "Rule id list can not be empty.")
    private List<@NotNull(message = "Rule id can not be null.") UUID> ruleIds;
}
