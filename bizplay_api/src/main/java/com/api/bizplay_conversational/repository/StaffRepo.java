package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.Department;
import com.api.bizplay_conversational.model.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffRepo extends JpaRepository<Staff, UUID> {
    Optional<Staff> findByCorpNoAndNameAndDepartmentAndPosition(
            String corpNo,
            String name,
            Department department,
            String position
    );
}
