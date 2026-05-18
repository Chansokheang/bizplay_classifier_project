package com.api.bizplay_chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Pins Spring Data Auditing's {@code @CreatedDate} / {@code @LastModifiedDate}
 * to the application's canonical zone (see {@link AppZone}) regardless of what
 * the JVM thinks {@code Clock.systemDefaultZone()} is.
 *
 * <p>Why we need this even though the container also sets {@code TZ=Asia/Seoul}:
 * the container env var is the primary defense, but a misconfigured JVM,
 * a forgotten {@code -Duser.timezone} flag, or a rebuild that drops the
 * compose env block would silently start writing rows with a different zone's
 * naive timestamps. Auditing pinned at the Spring level guarantees that
 * doesn't happen, and any discrepancy with what wall-clock logs show becomes
 * an immediately visible signal that the container env is wrong.
 *
 * <p>This class also owns {@link EnableJpaAuditing} — the bare annotation was
 * previously on {@code BizPlayChatbotApplication} but had to move here so we
 * could pass {@code dateTimeProviderRef} to point at the bean below.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstAuditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * Returns "now" in {@link AppZone#APPLICATION_ZONE} as a naive
     * {@link LocalDateTime}, matching the column type the entities use
     * ({@code TIMESTAMP WITHOUT TIME ZONE}).
     */
    @Bean
    public DateTimeProvider kstAuditingDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(AppZone.APPLICATION_ZONE));
    }
}
