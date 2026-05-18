package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.Corporation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorporationRepository extends JpaRepository<Corporation, Long> {

    /** Look up a corporation by its natural business identifier (corp_no). */
    Optional<Corporation> findByCorpNo(String corpNo);

    /** Cheap existence check used by the corp CRUD layer to detect collisions
     *  on create without loading the row. */
    boolean existsByCorpNo(String corpNo);

    /** Filter for the optional `?corpGroupId=` query param on `GET /corps`. */
    java.util.List<Corporation> findAllByCorpGroupIdOrderByCorpNoAsc(Long corpGroupId);

    /** Default ordering for the unfiltered list endpoint. */
    java.util.List<Corporation> findAllByOrderByCorpNoAsc();

    /** Used by the corp_group delete guard to decide whether the group is empty. */
    long countByCorpGroupId(Long corpGroupId);
}
