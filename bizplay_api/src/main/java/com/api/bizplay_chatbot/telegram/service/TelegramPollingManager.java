package com.api.bizplay_chatbot.telegram.service;

import com.api.bizplay_chatbot.config.TelegramProperties;
import com.api.bizplay_chatbot.domain.entity.Bot;
import com.api.bizplay_chatbot.domain.repository.BotRepository;
import com.api.bizplay_chatbot.telegram.client.TelegramApiClient;
import com.api.bizplay_chatbot.telegram.client.TelegramApiException;
import com.api.bizplay_chatbot.telegram.dto.Update;
import com.api.bizplay_chatbot.telegram.event.TelegramBotConfiguredEvent;
import com.api.bizplay_chatbot.telegram.event.TelegramBotUnconfiguredEvent;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the per-bot Telegram long-polling threads. One daemon thread per
 * configured bot: it sits in a loop calling {@code getUpdates(offset, timeout)}
 * against Telegram, hands each returned update to {@link TelegramBotService}
 * for processing, then advances the offset (acknowledging the update to
 * Telegram and persisting it locally so a restart doesn't replay).
 *
 * <p>Why threads, not the Spring task executor: long-poll calls block on the
 * socket for up to {@code longPollTimeoutSeconds}. A short-lived task pool
 * would have every worker pinned in I/O wait at all times, making the
 * abstraction useless. Dedicated named daemon threads make logs easier to
 * read ({@code tg-poller-<botId>}) and shutdown straightforward
 * (interrupt + join).
 *
 * <p>Single-instance assumption: Telegram returns HTTP 409 if two processes
 * call {@code getUpdates} for the same bot simultaneously. A multi-replica
 * deployment would need leader election (or distributed locks) before
 * starting pollers. We log conflicts at WARN with the long backoff so the
 * issue surfaces in operator logs without hammering Telegram.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramPollingManager {

    /** Telegram update kinds we actually want. Other kinds (edited_message,
     *  channel_post, …) are filtered server-side so we never have to handle
     *  them locally. */
    private static final List<String> ALLOWED_UPDATE_KINDS = List.of("message", "callback_query");

    private final BotRepository botRepository;
    private final TelegramApiClient apiClient;
    private final TelegramBotService botService;
    private final TelegramProperties props;

    /** Tracks every active poller so configure/unconfigure events and
     *  shutdown can find and interrupt them. ConcurrentHashMap because the
     *  event-driven start/stop calls can race with each other and with
     *  shutdown — the map operations are the synchronisation point. */
    private final Map<UUID, Thread> pollers = new ConcurrentHashMap<>();

    /** Set during {@link #shutdown()} so any in-flight poll cycle that wakes
     *  from interruption knows not to re-enter the loop. */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // ─── Lifecycle ────────────────────────────────────────────────────────

    /** Start pollers for every already-configured bot at application boot.
     *  Listening for {@link ApplicationReadyEvent} (not {@code @PostConstruct})
     *  so JPA, the chat client registry, and the rest of the context are all
     *  initialised before any poller can fire its first update. */
    @EventListener(ApplicationReadyEvent.class)
    public void startupAll() {
        List<Bot> configured = botRepository.findAllByTelegramBotTokenIsNotNull();
        log.info("Starting Telegram pollers for {} bot(s) at boot", configured.size());
        for (Bot bot : configured) {
            startPollerFor(bot.getId(), bot.getTelegramBotToken());
        }
    }

    @EventListener
    public void onConfigured(TelegramBotConfiguredEvent ev) {
        startPollerFor(ev.botId(), ev.token());
    }

    @EventListener
    public void onUnconfigured(TelegramBotUnconfiguredEvent ev) {
        stopPollerFor(ev.botId());
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown.set(true);
        long timeoutMs = props.getShutdownTimeout().toMillis();
        log.info("Stopping {} Telegram poller(s) (timeout {} ms)", pollers.size(), timeoutMs);
        // Two-pass: interrupt every thread first so they all start waking up
        // in parallel, then join them. Interrupts cancel both Thread.sleep
        // backoffs and the long-poll HTTP socket read (via the JDK client's
        // interrupt-aware behaviour).
        pollers.values().forEach(Thread::interrupt);
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (Thread t : pollers.values()) {
            long remaining = Math.max(1, deadline - System.currentTimeMillis());
            try {
                t.join(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        pollers.clear();
    }

    // ─── Start / stop ─────────────────────────────────────────────────────

    private void startPollerFor(UUID botId, String token) {
        // Replace-on-restart semantics: rotate-token or webhook→polling
        // migrations may fire a Configured event for a bot that already has
        // a running poller. Stop the old one first so we don't end up with
        // two threads racing for the same Telegram queue.
        Thread existing = pollers.remove(botId);
        if (existing != null) {
            existing.interrupt();
            try {
                existing.join(props.getShutdownTimeout().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Thread t = new Thread(() -> pollLoop(botId, token), "tg-poller-" + botId);
        t.setDaemon(true);
        pollers.put(botId, t);
        t.start();
        log.info("Started Telegram poller: botId={}, thread={}", botId, t.getName());
    }

    private void stopPollerFor(UUID botId) {
        Thread t = pollers.remove(botId);
        if (t == null) return;
        t.interrupt();
        try {
            t.join(props.getShutdownTimeout().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Stopped Telegram poller: botId={}", botId);
    }

    // ─── The poll loop ────────────────────────────────────────────────────

    private void pollLoop(UUID botId, String token) {
        // Load the persisted offset once at thread start. We then maintain it
        // locally and write through on every successful update; the DB read
        // is only needed on cold start to avoid replaying queued messages.
        Long offset = botRepository.findById(botId)
                .map(Bot::getTelegramLastOffset)
                .orElse(null);

        log.info("Telegram poller running: botId={}, startOffset={}", botId, offset);

        while (!Thread.currentThread().isInterrupted() && !shuttingDown.get()) {
            try {
                List<Update> updates = apiClient.getUpdates(
                        token, offset, props.getLongPollTimeoutSeconds(), ALLOWED_UPDATE_KINDS);

                for (Update u : updates) {
                    // Synchronous per-update processing serialises per-bot —
                    // a slow LLM call delays subsequent updates for the same
                    // bot, never for other bots. This is the right model: a
                    // single Telegram user shouldn't have their next reply
                    // overtake the previous one.
                    botService.processUpdate(botId, u);

                    offset = u.getUpdateId() + 1;
                    // Persist after each update — not after each batch — so
                    // a crash mid-batch doesn't replay already-processed
                    // updates on restart. Cost is one tiny UPDATE per
                    // message, which is fine compared to the LLM call.
                    botRepository.updateTelegramLastOffset(botId, offset);
                }
            } catch (TelegramApiException e) {
                // 409 Conflict = either a webhook still exists or another
                // instance is already polling this bot. Long backoff so
                // operator logs stay readable.
                boolean conflict = e.getMessage() != null
                        && (e.getMessage().contains("Conflict")
                            || e.getMessage().contains("409"));
                long backoffMs = (conflict
                        ? props.getPollConflictBackoff()
                        : props.getPollRetryBackoff()).toMillis();
                log.warn("Telegram getUpdates failed for botId={}: {} — retrying in {} ms",
                        botId, e.getMessage(), backoffMs);
                if (sleepInterruptibly(backoffMs)) break;
            } catch (RuntimeException e) {
                // Defensive: any other unexpected exception in processUpdate
                // must not kill the polling loop. Log loud, back off, keep
                // going — better to keep delivering than to silently stop.
                log.error("Unexpected error in Telegram poll loop for botId={}: {}",
                        botId, e.getMessage(), e);
                if (sleepInterruptibly(props.getPollRetryBackoff().toMillis())) break;
            }
        }

        log.info("Telegram poller exited: botId={}", botId);
    }

    /** Sleep that respects interruption — returns true if interrupted (caller
     *  should break out of the loop). */
    private boolean sleepInterruptibly(long ms) {
        try {
            Thread.sleep(ms);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
