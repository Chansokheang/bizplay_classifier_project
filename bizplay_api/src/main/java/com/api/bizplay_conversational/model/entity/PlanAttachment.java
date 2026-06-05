package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PlanAttachment {
    private UUID id;
    private Plan plan;
    private String type;
    private String fileId;
    private String url;
    private LocalDateTime createdAt;
}
