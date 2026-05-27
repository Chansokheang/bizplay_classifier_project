package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PlanAttachmentResponse {
    private UUID id;
    private String type;
    private String fileId;
    private String url;
}
