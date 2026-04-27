package com.api.bizplay_classifier_api.service.corpService;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.CorpDTO;
import com.api.bizplay_classifier_api.model.dto.CorpGroupDTO;
import com.api.bizplay_classifier_api.model.request.CorpRequest;
import com.api.bizplay_classifier_api.model.request.CorpGroupRequest;
import com.api.bizplay_classifier_api.model.response.CorpResponse;
import com.api.bizplay_classifier_api.model.response.CorpGroupResponse;
import com.api.bizplay_classifier_api.repository.CorpRepo;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Log4j2
@Service
@AllArgsConstructor
public class CorpServiceImple implements CorpService {

    private final CorpRepo corpRepo;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public CorpGroupResponse createCorpGroup(CorpGroupRequest corpGroupRequest) {
        String corpGroupCode = normalizeCorpGroupCode(corpGroupRequest.getCorpGroupCode());
        if (corpRepo.existsCorpGroupByCode(corpGroupCode)) {
            throw new IllegalArgumentException("Corp group code " + corpGroupCode + " is already registered.");
        }

        CorpGroupDTO corpGroupDTO = corpRepo.createCorpGroup(corpGroupCode);
        return modelMapper.map(corpGroupDTO, CorpGroupResponse.class);
    }

    @Override
    public List<CorpGroupResponse> getAllCorpGroups() {
        return corpRepo.getAllCorpGroups().stream()
                .map(corpGroupDTO -> modelMapper.map(corpGroupDTO, CorpGroupResponse.class))
                .toList();
    }

    @Override
    public CorpGroupResponse getCorpGroupById(Long corpGroupId) {
        CorpGroupDTO corpGroupDTO = corpRepo.getCorpGroupById(corpGroupId);
        if (corpGroupDTO == null) {
            throw new CustomNotFoundException("Corp group was not found with Id: " + corpGroupId);
        }
        return modelMapper.map(corpGroupDTO, CorpGroupResponse.class);
    }

    @Override
    public List<CorpResponse> getAllCorps() {
        List<CorpDTO> corpDTOList = corpRepo.getAllCorps();
        return corpDTOList.stream()
                .map(corpDTO -> modelMapper.map(corpDTO, CorpResponse.class))
                .toList();
    }

    @Override
    @Transactional
    public CorpResponse createCorp(CorpRequest corpRequest) {
        corpRequest.setCorpNo(normalizeBusinessNumber(corpRequest.getCorpNo()));
        if (corpRepo.existsBycorpNo(corpRequest.getCorpNo())) {
            throw new IllegalArgumentException("Business number " + corpRequest.getCorpNo() + " is already registered.");
        }
        if (!corpRepo.existsCorpGroupById(corpRequest.getCorpGroupId())) {
            throw new CustomNotFoundException("Corp group was not found with Id: " + corpRequest.getCorpGroupId());
        }
        CorpDTO corpDTO = corpRepo.createCorp(corpRequest, corpRequest.getCorpNo());
        CorpDTO createdCorp = corpRepo.getCorpByCorpNo(corpDTO.getCorpNo());
        return modelMapper.map(createdCorp, CorpResponse.class);
    }

    @Override
    public CorpResponse getCorpByCorpNo(String corpNo) {
        CorpDTO corpDTO = corpRepo.getCorpByCorpNo(corpNo);
        if (corpDTO == null) {
            throw new CustomNotFoundException("Corp was not found with Id: " + corpNo);
        }
        return modelMapper.map(corpDTO, CorpResponse.class);
    }

    @Override
    @Transactional
    public void deleteCorpByCorpNo(String corpNo) {
        int deletedRows = corpRepo.deleteCorpByCorpNo(corpNo);
        if (deletedRows == 0) {
            throw new CustomNotFoundException("Corp was not found with Id: " + corpNo);
        }
    }

    private String normalizeBusinessNumber(String businessNumber) {
        if (businessNumber == null || businessNumber.isBlank()) {
            throw new IllegalArgumentException("Business number is required.");
        }

        String digitsOnly = businessNumber.replaceAll("\\D", "");
        if (digitsOnly.length() != 10) {
            throw new IllegalArgumentException("Business number must be exactly 10 digits.");
        }
        return digitsOnly;
    }

    private String normalizeCorpGroupCode(String corpGroupCode) {
        if (corpGroupCode == null || corpGroupCode.isBlank()) {
            throw new IllegalArgumentException("Corp group code is required.");
        }

        String normalized = corpGroupCode.trim().replaceAll("\\s+", "_");
        if (normalized.length() > 20) {
            throw new IllegalArgumentException("Corp group code must be 20 characters or fewer.");
        }
        return normalized;
    }

}
