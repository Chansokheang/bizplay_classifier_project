package com.api.bizplay_chatbot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the {@code corp_group} table — top of the tenant tree, groups
 * related {@link Corporation} rows together. {@code corp_group_id} is a
 * BIGSERIAL surrogate key; {@code corp_group_cd} is the natural business
 * code (UNIQUE, length 20).
 *
 * No bidirectional navigation to {@link Corporation} on purpose — the
 * relationship is queried by id when needed. Add an {@code @OneToMany} only
 * if a future feature actually traverses it.
 */
@Entity
@Table(name = "corp_group")
@Getter
@Setter
@NoArgsConstructor
public class CorpGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "corp_group_id")
    private Long id;

    @Column(name = "corp_group_cd", nullable = false, length = 20)
    private String corpGroupCd;
}
