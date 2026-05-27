package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanRepo extends JpaRepository<Plan, UUID> {
}
