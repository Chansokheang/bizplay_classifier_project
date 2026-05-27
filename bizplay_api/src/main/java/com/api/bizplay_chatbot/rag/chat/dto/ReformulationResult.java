package com.api.bizplay_chatbot.rag.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReformulationResult {
    private final QueryIntent intent;
    private final String reformulatedQuery;
}
