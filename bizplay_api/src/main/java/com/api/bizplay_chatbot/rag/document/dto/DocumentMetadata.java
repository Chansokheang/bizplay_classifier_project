package com.api.bizplay_chatbot.rag.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class DocumentMetadata {

    @NotBlank(message = "Title is required")
    private String title;
}
