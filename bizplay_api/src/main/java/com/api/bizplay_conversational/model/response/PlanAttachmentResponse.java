package com.api.bizplay_conversational.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanAttachmentResponse {
    private UUID id;
    private String type;
    private String fileId;
    private String url;
}
