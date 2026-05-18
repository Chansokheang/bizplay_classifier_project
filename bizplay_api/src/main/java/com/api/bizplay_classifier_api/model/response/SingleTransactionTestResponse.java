package com.api.bizplay_classifier_api.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SingleTransactionTestResponse {
    private UUID fileId;
    private String corpNo;
    private String categoryNo;
    private String categoryName;
    private String method;
    private String reason;
}
