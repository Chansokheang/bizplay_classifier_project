package com.api.bizplay_chatbot.domain.repository;

import com.api.bizplay_chatbot.domain.entity.CorpGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorpGroupRepository extends JpaRepository<CorpGroup, Long> {

    /** Look up by the natural business code. */
    Optional<CorpGroup> findByCorpGroupCd(String corpGroupCd);

    /** Cheap existence check used to detect collisions on create. */
    boolean existsByCorpGroupCd(String corpGroupCd);

    /** Default ordering for the list endpoint. */
    List<CorpGroup> findAllByOrderByCorpGroupCdAsc();
}
