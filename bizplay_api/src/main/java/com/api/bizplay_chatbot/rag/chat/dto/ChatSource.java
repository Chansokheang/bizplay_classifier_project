package com.api.bizplay_chatbot.rag.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatSource {
    private String docId;
    private String title;
    private String fileName;
    private String snippet;
    private double score;
    private int chunkIndex;
    private String documentUrl;
}
