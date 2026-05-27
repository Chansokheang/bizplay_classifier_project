package com.api.bizplay_chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Binds the {@code app.telegram.*} config block. Keeps Telegram-specific
 * tunables out of @{@code @Value} sprinkled across services.
 *
 * <p>Long polling: the application pulls updates from Telegram via getUpdates
 * from a dedicated thread per configured bot. No inbound URL is required —
 * only outbound HTTPS to api.telegram.org. This is the right transport for
 * internal-only deployments where the RAG API has no public ingress.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.telegram")
public class TelegramProperties {

    /** Base URL of the Telegram Bot API. Override only for tests / a private
     *  proxy. The bot token is appended per-call as {@code /bot{token}/method}
     *  so this stays public. */
    private String apiBaseUrl = "https://api.telegram.org";

    /** Read timeout for outgoing calls to Telegram. Must be greater than
     *  {@link #longPollTimeoutSeconds} because Telegram holds the getUpdates
     *  connection open until either an update arrives or the long-poll
     *  timeout expires — if the HTTP client gives up first, every empty poll
     *  cycle would surface as a transport error. Default 35s gives a 5s
     *  margin over the 30s long-poll. */
    private Duration httpTimeout = Duration.ofSeconds(35);

    /** Telegram-side long-poll timeout in seconds — how long Telegram should
     *  hold the connection open before returning an empty update list.
     *  Telegram's documented max is 50; 30 strikes a balance between idle
     *  connection cost and reconnection frequency. */
    private int longPollTimeoutSeconds = 30;

    /** Backoff between getUpdates retries when Telegram returns a transient
     *  error (5xx, network blip). */
    private Duration pollRetryBackoff = Duration.ofSeconds(5);

    /** Backoff when getUpdates returns 409 Conflict — usually means another
     *  process is also polling for the same bot, or a webhook is still
     *  registered. Long backoff avoids hammering Telegram while a human
     *  operator fixes the conflict. */
    private Duration pollConflictBackoff = Duration.ofSeconds(30);

    /** How long to wait for each per-bot polling thread to exit during
     *  application shutdown before giving up and proceeding. */
    private Duration shutdownTimeout = Duration.ofSeconds(10);
}
