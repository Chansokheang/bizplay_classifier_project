package com.api.bizplay_chatbot.corp.service;

import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.corp.dto.CorpCreateRequest;
import com.api.bizplay_chatbot.corp.dto.CorpResponse;
import com.api.bizplay_chatbot.corp.dto.CorpUpdateRequest;
import com.api.bizplay_chatbot.domain.entity.Corporation;
import com.api.bizplay_chatbot.domain.repository.CorpGroupRepository;
import com.api.bizplay_chatbot.domain.repository.CorporationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for {@code corp} rows. {@code corp_no} is the API path identifier and
 * is immutable on update — to change a corp's natural code, delete and
 * re-create. Bots reference corps by {@code corp_no} as a soft reference, so
 * deleting a corp does not affect bots beyond leaving their {@code corpNo}
 * dangling (which the system tolerates by design).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorpService {

    private final CorporationRepository corporationRepository;
    private final CorpGroupRepository corpGroupRepository;

    @Transactional(readOnly = true)
    public List<CorpResponse> list(Long corpGroupId) {
        List<Corporation> rows = (corpGroupId == null)
                ? corporationRepository.findAllByOrderByCorpNoAsc()
                : corporationRepository.findAllByCorpGroupIdOrderByCorpNoAsc(corpGroupId);
        return rows.stream().map(CorpService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CorpResponse get(String corpNo) {
        return toResponse(loadOrThrow(corpNo));
    }

    @Transactional
    public CorpResponse create(CorpCreateRequest req) {
        if (corporationRepository.existsByCorpNo(req.getCorpNo())) {
            throw new BusinessException(
                    "Corporation already exists: corp_no=" + req.getCorpNo(),
                    HttpStatus.CONFLICT);
        }
        if (!corpGroupRepository.existsById(req.getCorpGroupId())) {
            throw new BusinessException(
                    "Corp group not found: id=" + req.getCorpGroupId(),
                    HttpStatus.NOT_FOUND);
        }
        Corporation c = new Corporation();
        c.setCorpNo(req.getCorpNo());
        c.setCorpGroupId(req.getCorpGroupId());
        c.setCorpName(req.getCorpName());
        Corporation saved = corporationRepository.save(c);
        log.info("Corporation created: id={}, corp_no={}", saved.getId(), saved.getCorpNo());
        return toResponse(saved);
    }

    /**
     * PATCH semantics. {@code corpNo} is the path identifier and is NOT
     * updatable here.
     */
    @Transactional
    public CorpResponse update(String corpNo, CorpUpdateRequest req) {
        Corporation c = loadOrThrow(corpNo);
        if (req.getCorpGroupId() != null) {
            if (!corpGroupRepository.existsById(req.getCorpGroupId())) {
                throw new BusinessException(
                        "Corp group not found: id=" + req.getCorpGroupId(),
                        HttpStatus.NOT_FOUND);
            }
            c.setCorpGroupId(req.getCorpGroupId());
        }
        if (req.getCorpName() != null) c.setCorpName(req.getCorpName());
        log.info("Corporation updated: corp_no={}", corpNo);
        return toResponse(corporationRepository.save(c));
    }

    @Transactional
    public void delete(String corpNo) {
        Corporation c = loadOrThrow(corpNo);
        corporationRepository.delete(c);
        log.info("Corporation deleted: corp_no={}", corpNo);
    }

    private Corporation loadOrThrow(String corpNo) {
        return corporationRepository.findByCorpNo(corpNo).orElseThrow(() ->
                new BusinessException("Corporation not found: corp_no=" + corpNo, HttpStatus.NOT_FOUND));
    }

    private static CorpResponse toResponse(Corporation c) {
        return CorpResponse.builder()
                .id(c.getId())
                .corpNo(c.getCorpNo())
                .corpGroupId(c.getCorpGroupId())
                .corpName(c.getCorpName())
                .createdDate(c.getCreatedDate())
                .build();
    }
}
