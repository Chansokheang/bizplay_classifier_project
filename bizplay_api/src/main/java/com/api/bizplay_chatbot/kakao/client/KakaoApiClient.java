package com.api.bizplay_chatbot.kakao.client;

import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.kakao.dto.SkillResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Outbound HTTP wrapper for Kakao's callback-delivery URL. Unlike Telegram
 * — where we hold a long token and call many methods — Kakao gives us a
 * one-shot {@code callbackUrl} per turn that's only valid for ~1 minute.
 * So this client just has one operation: POST a final {@link SkillResponse}
 * to the given URL and surface failures as {@link BusinessException}s.
 *
 * <p>The URL is fully qualified (Kakao chooses it), so we use a non-base-URL
 * RestClient and pass the URL each call.
 */
@Slf4j
@Component
public class KakaoApiClient {

    private final RestClient restClient;

    public KakaoApiClient(@Qualifier("kakaoRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** POST the final answer to Kakao's callback URL. Throws on transport
     *  errors and on 4xx/5xx responses — Kakao's body in either case usually
     *  contains a JSON message which we surface in the log. */
    public void postCallback(String callbackUrl, SkillResponse body) {
        try {
            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            log.warn("Kakao callback POST failed: status={}, body={}",
                    ex.getStatusCode(), responseBody);
            throw new BusinessException(
                    "Kakao callback failed: HTTP " + ex.getStatusCode(),
                    HttpStatus.BAD_GATEWAY);
        } catch (ResourceAccessException ex) {
            log.warn("Kakao callback unreachable: {}", ex.getMessage());
            throw new BusinessException(
                    "Kakao callback unreachable: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
    }
}
