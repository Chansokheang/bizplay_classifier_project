package com.api.bizplay_classifier_api.model.dto;

import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BotConfigDTO {
    private UUID botId;
    private String companyId;
    private BotConfigRequest.Config config;
    @JsonIgnore
    private String rawConfig;
    private Timestamp createdDate;
}
