package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepo extends JpaRepository<Department, UUID> {
    Optional<Department> findByCorpNoAndName(String corpNo, String name);
}
