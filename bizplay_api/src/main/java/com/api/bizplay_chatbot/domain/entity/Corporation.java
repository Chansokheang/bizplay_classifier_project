package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Maps to the {@code corp} table — {@code corp_id} is a BIGSERIAL, but
 * {@code corp_no} is the natural business identifier issued by the external
 * login service and is what the rest of the system references (e.g.
 * {@code Bot.corpNo}). {@code corp_group_id} groups related corporations.
 *
 * The seeded row at {@code corp_no = 'DEFAULT'} is used as the default
 * tenant fallback when callers omit {@code corpNo} on bot create.
 *
 * Schema note: {@code corp_no} was widened from CHAR(10) to VARCHAR(50) in
 * V3 to accommodate the login service's wider identifier format.
 */
@Entity
@Table(name = "corp")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Corporation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "corp_id")
    private Long id;

    /** Natural business identifier — issued by the external login service and
     *  used by {@link Bot#getCorpNo()} as a soft reference. */
    @Column(name = "corp_no", nullable = false, length = 50)
    private String corpNo;

    @Column(name = "corp_group_id", nullable = false)
    private Long corpGroupId;

    @Column(name = "corp_name", nullable = false, length = 255)
    private String corpName;

    /**
     * Populated by Spring Data Auditing's {@link AuditingEntityListener}, which
     * routes through the application-wide KST {@code DateTimeProvider}
     * configured in {@code JpaAuditingConfig}. The DB-side {@code DEFAULT NOW()}
     * was dropped in V8 because it ran in PostgreSQL's session timezone — not
     * the app's pinned {@code Asia/Seoul} — and produced corp rows with
     * silently UTC-stored timestamps while every other table was KST.
     * Now the entity owns the value, matching {@code Bot.createdAt},
     * {@code ChatSession.createdAt}, etc.
     */
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
}
