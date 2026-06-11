package com.api.bizplay_conversational.service.departmentService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.Department;
import com.api.bizplay_conversational.model.request.DepartmentRequest;
import com.api.bizplay_conversational.model.response.DepartmentResponse;
import com.api.bizplay_conversational.repository.DepartmentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImple implements DepartmentService {

    private final DepartmentRepo departmentRepo;

    @Override
    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String corpNo = blankToNull(request.getCorpNo());
        String name = blankToNull(request.getName());
        if (corpNo == null) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        if (departmentRepo.findByCorpNoAndName(corpNo, name).isPresent()) {
            throw new IllegalArgumentException("Department '" + name + "' already exists for corpNo=" + corpNo + ".");
        }
        Department department = new Department();
        department.setCorpNo(corpNo);
        department.setName(name);
        departmentRepo.save(department);
        return toResponse(departmentRepo.findById(department.getId().toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getByCorpNo(String corpNo) {
        List<DepartmentResponse> result = new ArrayList<>();
        for (Department d : departmentRepo.findByCorpNo(corpNo)) {
            result.add(toResponse(d));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getById(String id) {
        Department d = departmentRepo.findById(parseId(id).toString());
        if (d == null) {
            throw new CustomNotFoundException("Department not found: " + id);
        }
        return toResponse(d);
    }

    @Override
    @Transactional
    public DepartmentResponse update(String id, DepartmentRequest request) {
        UUID departmentId = parseId(id);
        Department existing = departmentRepo.findById(departmentId.toString());
        if (existing == null) {
            throw new CustomNotFoundException("Department not found: " + id);
        }
        String name = blankToNull(request.getName());
        if (name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        // Block renaming onto another department's name in the same corp.
        if (!name.equalsIgnoreCase(existing.getName())) {
            departmentRepo.findByCorpNoAndName(existing.getCorpNo(), name).ifPresent(d -> {
                throw new IllegalArgumentException(
                        "Department '" + name + "' already exists for corpNo=" + existing.getCorpNo() + ".");
            });
        }
        departmentRepo.updateName(departmentId, name);
        return toResponse(departmentRepo.findById(departmentId.toString()));
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        UUID departmentId = parseId(id);
        Department existing = departmentRepo.findById(departmentId.toString());
        if (existing == null) {
            throw new CustomNotFoundException("Department not found: " + id);
        }
        int staff = departmentRepo.countStaff(departmentId);
        if (staff > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete department '" + existing.getName() + "': it still has " + staff
                            + " staff member(s). Reassign or delete them first.");
        }
        departmentRepo.deleteById(departmentId);
    }

    private DepartmentResponse toResponse(Department d) {
        return DepartmentResponse.builder()
                .id(d.getId())
                .corpNo(d.getCorpNo())
                .name(d.getName())
                .createdAt(d.getCreatedDate())
                .build();
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id == null ? null : id.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomNotFoundException("Invalid department id: " + id);
        }
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
