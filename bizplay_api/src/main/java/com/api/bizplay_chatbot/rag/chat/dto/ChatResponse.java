package com.api.bizplay_chatbot.rag.chat.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ChatResponse {
    private String answer;
    private UUID sessionId;
    private List<ChatSource> sources;
}
