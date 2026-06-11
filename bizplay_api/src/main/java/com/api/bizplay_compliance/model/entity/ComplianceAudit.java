package com.api.bizplay_compliance.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A persisted row of compliance_audit: one aggregate R10 audit result for a trip plan. The
 * per-dimension findings are stored in {@code rulesJson} (JSONB). {@code reportId} is null in
 * aggregate mode (one row per plan rather than per report line).
 */
@Getter
@Setter
@NoArgsConstructor
@lombok.AllArgsConstructor
@Builder
public class ComplianceAudit {
    private UUID id;
    private String corpNo;
    private UUID tripPlanId;
    private UUID reportId;
    private String complianceStatus;
    private String confidenceLevel;
    private JsonNode rulesJson;
    private LocalDateTime createdDate;
}
