package com.api.bizplay_chatbot.config;

import java.time.ZoneId;

/**
 * Single source of truth for the application's timezone.
 *
 * <p>Three layers must agree on this value:
 * <ol>
 *   <li>The container's {@code TZ} env var (set in docker-compose.* files)
 *       — drives the JVM's {@code Clock.systemDefaultZone()} and any shell
 *       tools / log timestamps inside the container.</li>
 *   <li>Spring Data Auditing's {@code DateTimeProvider} — populates
 *       {@code @CreatedDate} fields. Wired via {@code JpaAuditingConfig} so
 *       the timestamp written to the DB is correct even if the JVM's default
 *       zone has somehow drifted.</li>
 *   <li>The analytics service's window math — {@code BotService.getStatistics}
 *       et al. compute "now" against this zone so the dashboard's "last N
 *       days" + daily-bar buckets line up with the user's calendar.</li>
 * </ol>
 *
 * <p>Mismatched zones cause subtle bugs: window edges drift by the offset and
 * the daily bar-chart attributes events to the wrong day. Locking everything
 * to one constant prevents that.
 *
 * <p>Korea doesn't observe DST, so the named zone and a fixed offset of
 * {@code +09:00} behave identically — the named zone is preferred so a future
 * region change (e.g. America/Los_Angeles) just works.
 */
public final class AppZone {

    public static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Seoul");

    private AppZone() {
        // utility class
    }
}
