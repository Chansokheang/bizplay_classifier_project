package com.api.bizplay_chatbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * RestClient pointed at the Telegram Bot API. Uses the same HTTP/1.1-forced
 * JDK client pattern as {@link SpringAiConfig} for consistency.
 *
 * The bot token is appended per-call as part of the URL path
 * ({@code /bot{token}/{method}}), so this client carries no auth header.
 */
@Slf4j
@Configuration
public class TelegramHttpConfig {

    @Bean
    @Qualifier("telegramRestClient")
    public RestClient telegramRestClient(TelegramProperties props) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        requestFactory.setReadTimeout(props.getHttpTimeout());

        log.info("Telegram REST client ready: baseUrl={}, readTimeout={}",
                props.getApiBaseUrl(), props.getHttpTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(props.getApiBaseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
