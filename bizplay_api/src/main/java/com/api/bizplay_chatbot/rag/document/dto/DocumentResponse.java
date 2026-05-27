package com.api.bizplay_chatbot.rag.document.dto;

import com.api.bizplay_chatbot.domain.enums.EmbeddingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DocumentResponse {
    private UUID id;
    private UUID botId;
    private String title;
    private String fileName;
    private String contentType;
    private EmbeddingStatus embeddingStatus;
    private LocalDateTime createdAt;
}
