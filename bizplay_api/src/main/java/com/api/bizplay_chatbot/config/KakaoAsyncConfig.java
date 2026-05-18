package com.api.bizplay_chatbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async + HTTP wiring for the Kakao integration.
 *
 * <p>Kakao's 5-second response budget forces us into the callback pattern:
 * the webhook controller must return immediately, but the actual LLM call
 * takes 10-30s. That work runs on this dedicated pool. {@code @EnableAsync}
 * is local to this config — Telegram intentionally doesn't use {@code @Async}
 * anymore so there's no clash.
 *
 * <p>Also exposes a {@code kakaoRestClient} bean for the outbound callback
 * POST. HTTP/1.1 is forced for consistency with the rest of the app
 * (vLLM dropped HTTP/2 POST bodies; the same JDK client config is reused).
 */
@Slf4j
@Configuration
@EnableAsync
public class KakaoAsyncConfig {

    @Bean(name = "kakaoTaskExecutor")
    public ThreadPoolTaskExecutor kakaoTaskExecutor(KakaoProperties props) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(props.getAsyncCorePoolSize());
        ex.setMaxPoolSize(props.getAsyncMaxPoolSize());
        ex.setQueueCapacity(props.getAsyncQueueCapacity());
        ex.setThreadNamePrefix("kakao-worker-");
        // CallerRunsPolicy gives natural backpressure under overload — the
        // webhook controller blocks instead of dropping the update, slowing
        // Kakao's delivery rate rather than losing the message.
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        log.info("Kakao async executor ready: core={}, max={}, queue={}",
                props.getAsyncCorePoolSize(), props.getAsyncMaxPoolSize(), props.getAsyncQueueCapacity());
        return ex;
    }

    @Bean
    @Qualifier("kakaoRestClient")
    public RestClient kakaoRestClient(KakaoProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        factory.setReadTimeout(props.getHttpTimeout());
        log.info("Kakao REST client ready: readTimeout={}", props.getHttpTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
