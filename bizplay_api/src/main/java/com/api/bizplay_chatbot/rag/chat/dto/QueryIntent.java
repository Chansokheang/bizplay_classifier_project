package com.api.bizplay_chatbot.rag.chat.dto;

public enum QueryIntent {
    /** Needs document retrieval (+ history, which is always included) */
    RETRIEVE,
    /** Can be answered from conversation history alone (translate, summarize, reformat, etc.) */
    HISTORY_ONLY
}
