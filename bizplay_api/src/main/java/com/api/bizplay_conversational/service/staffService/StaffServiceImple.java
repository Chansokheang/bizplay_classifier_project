package com.api.bizplay_conversational.service.staffService;

import com.api.bizplay_conversational.model.entity.Staff;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.repository.StaffRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImple implements StaffService {

    private final StaffRepo staffRepo;

    @Override
    @Transactional(readOnly = true)
    public StaffLookupResult lookup(String corpNo, String name) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        if (name == null || name.isBlank()) {
            return StaffLookupResult.builder()
                    .matched(false)
                    .corpNo(corpNo)
                    .matchType("NONE")
                    .message("No staff name was provided or extracted.")
                    .build();
        }

        String normalizedName = name.trim();
        List<Staff> exactMatches = staffRepo.findExactByCorpNoAndName(corpNo, normalizedName);
        if (!exactMatches.isEmpty()) {
            return toResult(corpNo, exactMatches.get(0), "EXACT");
        }

        List<Staff> partialMatches = staffRepo.searchByCorpNoAndName(corpNo, normalizedName);
        if (!partialMatches.isEmpty()) {
            return toResult(corpNo, partialMatches.get(0), "PARTIAL");
        }

        return StaffLookupResult.builder()
                .matched(false)
                .corpNo(corpNo)
                .matchType("NONE")
                .message("No staff found for name: " + normalizedName)
                .build();
    }

    private StaffLookupResult toResult(String corpNo, Staff staff, String matchType) {
        return StaffLookupResult.builder()
                .matched(true)
                .corpNo(corpNo)
                .staffId(staff.getId())
                .staffName(staff.getName())
                .departmentId(staff.getDepartment().getId())
                .departmentName(staff.getDepartment().getName())
                .position(staff.getPosition())
                .matchType(matchType)
                .message("Staff matched.")
                .build();
    }
}
