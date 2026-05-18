package com.api.bizplay_chatbot.kakao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoConfigureRequest {

    @Schema(description = "Optional display label for the linked Kakao i Openbuilder bot, e.g. "
            + "\"출장규정봇\". Surfaced in the bot editor UI only — Kakao does not provide a "
            + "canonical name in its webhook payloads, so the operator picks something memorable.",
            example = "출장규정봇")
    @Size(max = 255)
    private String botName;
}
