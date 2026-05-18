package com.api.bizplay_chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Binds the {@code app.kakao.*} config block.
 *
 * <p>KakaoTalk integration uses Kakao i Openbuilder Skill webhooks: Kakao's
 * servers POST to a public-facing URL we control. We can't long-poll Kakao
 * (no equivalent API exists), so the Telegram-style "internal-only"
 * deployment requires an edge proxy that exposes the {@code /chatbot/api/v1/kakao/webhook/**}
 * path. The rest of {@code /api/**} can stay internal.
 *
 * <p>The {@link #publicBaseUrl} is the externally-reachable origin operators
 * paste into Openbuilder's Skill configuration. The UI builds the full URL
 * by appending {@code /chatbot/api/v1/kakao/webhook/{botId}/{secret}}.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.kakao")
public class KakaoProperties {

    /** Externally-reachable origin (no trailing slash), e.g.
     *  {@code https://chat.example.com}. Required for configure/status to
     *  return a usable webhook URL. Without it, the UI can still link a bot
     *  but the displayed URL is incomplete. */
    private String publicBaseUrl;

    /** Read timeout for the outbound callback POST to Kakao. The callback
     *  URL must accept our response within ~1 minute per Kakao docs; 30s
     *  read timeout gives margin while still failing fast on network blips. */
    private Duration httpTimeout = Duration.ofSeconds(30);

    /** Synchronous placeholder sent within the 5-second ACK window before
     *  the real LLM answer is delivered via callback. Kept short so the
     *  user sees something fast. Korean default matches the expected
     *  primary user audience. */
    private String placeholderText = "잠시만 기다려 주세요. 답변을 준비하고 있습니다…";

    /** Async pool tuning for callback workers. Each thread is held for the
     *  full LLM call (10-30s), so size for expected peak Kakao concurrency. */
    private int asyncCorePoolSize = 4;
    private int asyncMaxPoolSize = 16;
    private int asyncQueueCapacity = 200;
}
