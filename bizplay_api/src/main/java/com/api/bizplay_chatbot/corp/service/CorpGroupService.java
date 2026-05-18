package com.api.bizplay_chatbot.corp.service;

import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.corp.dto.CorpGroupCreateRequest;
import com.api.bizplay_chatbot.corp.dto.CorpGroupResponse;
import com.api.bizplay_chatbot.corp.dto.CorpGroupUpdateRequest;
import com.api.bizplay_chatbot.domain.entity.CorpGroup;
import com.api.bizplay_chatbot.domain.repository.CorpGroupRepository;
import com.api.bizplay_chatbot.domain.repository.CorporationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for {@code corp_group} rows. Hard-FK from {@code corp.corp_group_id}
 * still applies, so deleting a group with any corp under it is rejected with
 * 409 — admins must clear the group before removing it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorpGroupService {

    private final CorpGroupRepository corpGroupRepository;
    private final CorporationRepository corporationRepository;

    @Transactional(readOnly = true)
    public List<CorpGroupResponse> list() {
        return corpGroupRepository.findAllByOrderByCorpGroupCdAsc().stream()
                .map(CorpGroupService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CorpGroupResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public CorpGroupResponse create(CorpGroupCreateRequest req) {
        if (corpGroupRepository.existsByCorpGroupCd(req.getCorpGroupCd())) {
            throw new BusinessException(
                    "Corp group already exists: corp_group_cd=" + req.getCorpGroupCd(),
                    HttpStatus.CONFLICT);
        }
        CorpGroup g = new CorpGroup();
        g.setCorpGroupCd(req.getCorpGroupCd());
        CorpGroup saved = corpGroupRepository.save(g);
        log.info("Corp group created: id={}, cd={}", saved.getId(), saved.getCorpGroupCd());
        return toResponse(saved);
    }

    @Transactional
    public CorpGroupResponse update(Long id, CorpGroupUpdateRequest req) {
        CorpGroup g = loadOrThrow(id);
        if (req.getCorpGroupCd() != null && !req.getCorpGroupCd().equals(g.getCorpGroupCd())) {
            if (corpGroupRepository.existsByCorpGroupCd(req.getCorpGroupCd())) {
                throw new BusinessException(
                        "Corp group already exists: corp_group_cd=" + req.getCorpGroupCd(),
                        HttpStatus.CONFLICT);
            }
            g.setCorpGroupCd(req.getCorpGroupCd());
        }
        log.info("Corp group updated: id={}", id);
        return toResponse(corpGroupRepository.save(g));
    }

    /**
     * Reject delete if any corp still references this group. The DB FK is
     * {@code ON DELETE CASCADE}, but cascading from this admin endpoint would
     * silently wipe an entire tenant tree — guard against that with an
     * explicit pre-check.
     */
    @Transactional
    public void delete(Long id) {
        CorpGroup g = loadOrThrow(id);
        long corpsInGroup = corporationRepository.countByCorpGroupId(id);
        if (corpsInGroup > 0) {
            throw new BusinessException(
                    "Cannot delete corp_group: " + corpsInGroup + " corp row(s) still reference it. "
                            + "Move or delete those corps first.",
                    HttpStatus.CONFLICT);
        }
        corpGroupRepository.delete(g);
        log.info("Corp group deleted: id={}", id);
    }

    private CorpGroup loadOrThrow(Long id) {
        return corpGroupRepository.findById(id).orElseThrow(() ->
                new BusinessException("Corp group not found: id=" + id, HttpStatus.NOT_FOUND));
    }

    private static CorpGroupResponse toResponse(CorpGroup g) {
        return CorpGroupResponse.builder()
                .id(g.getId())
                .corpGroupCd(g.getCorpGroupCd())
                .build();
    }
}
