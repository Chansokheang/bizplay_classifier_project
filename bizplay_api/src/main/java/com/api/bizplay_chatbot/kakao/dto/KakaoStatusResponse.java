package com.api.bizplay_chatbot.kakao.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Kakao-integration view of a bot. Returned by configure / status endpoints.
 *
 * <p>Crucially, {@link #webhookUrl} CONTAINS the per-bot secret (it's the
 * full URL operators paste into Openbuilder's Skill config). Treat the
 * whole URL as sensitive — never log it, never embed it in error messages,
 * and only return it from authenticated config endpoints.
 */
@Getter
@Builder
public class KakaoStatusResponse {
    private boolean kakaoConfigured;
    private String botName;
    /** Full URL (origin + path + secret) to paste into Kakao i Openbuilder.
     *  Null when not configured, or when {@code app.kakao.public-base-url}
     *  is not set (in which case the response also carries a warning). */
    private String webhookUrl;
    /** True when {@code app.kakao.public-base-url} is missing. The UI shows
     *  a warning hint so the operator knows the URL is for reference only
     *  and won't actually be reachable. */
    private boolean publicBaseUrlMissing;
    private LocalDateTime configuredAt;
}
