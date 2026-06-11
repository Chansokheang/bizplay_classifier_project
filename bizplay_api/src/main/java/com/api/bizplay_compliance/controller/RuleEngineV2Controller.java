package com.api.bizplay_compliance.controller;

import com.api.bizplay_compliance.model.entity.ComplianceAudit;
import com.api.bizplay_compliance.model.request.ComplianceAuditBatchDeleteRequest;
import com.api.bizplay_compliance.model.request.ComplianceAuditUpdateRequest;
import com.api.bizplay_compliance.model.request.RequisitionAuditRequest;
import com.api.bizplay_compliance.model.response.ApiResponse;
import com.api.bizplay_compliance.model.response.ComplianceAuditBatchDeleteResponse;
import com.api.bizplay_compliance.model.response.RequisitionAuditResponse;
import com.api.bizplay_compliance.service.ruleService.RuleEngineV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * v2 rule engine. Hosts the R10 (requisition mismatch) audit only: compares an approved trip plan
 * against its expense report + uploaded receipts. Separate from the v1 {@code /api/v1/rule-engine}
 * testing endpoints, which are left untouched.
 */
@RestController
@AllArgsConstructor
@Log4j2
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009", "https://bizplay-api.aiconvergencelab.com"})
@RequestMapping("/api/v2/rule-engine")
@Tag(
        name = "Rule Engine v2",
        description = "Audit an expense report against its approved trip plan (R10 requisition mismatch)."
)
public class RuleEngineV2Controller {

    private final RuleEngineV2Service ruleEngineV2Service;

    @PostMapping("/r10")
    @Operation(
            summary = "R10 - Requisition mismatch (plan vs. expense report)",
            description = "Loads the approved trip plan, its expense report lines, and the uploaded receipts, "
                    + "then checks whether the actual expenses align with what was approved (approval gate, "
                    + "date/location/amount alignment, receipt backing). Persists one aggregate audit row."
    )
    public ResponseEntity<ApiResponse<RequisitionAuditResponse>> auditR10(
            @RequestBody RequisitionAuditRequest request) {
        log.info("POST /api/v2/rule-engine/r10 - corpNo={}, tripPlanId={}",
                request.corpNo(), request.tripPlanId());
        RequisitionAuditResponse result =
                ruleEngineV2Service.auditRequisition(request.corpNo(), request.tripPlanId());
        return ResponseEntity.ok(
                ApiResponse.<RequisitionAuditResponse>builder()
                        .payload(result)
                        .message("Run R10 audit successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/audits")
    @Operation(
            summary = "Get all compliance audits by corpNo",
            description = "Returns every persisted compliance audit for a corp, newest first."
    )
    public ResponseEntity<ApiResponse<List<ComplianceAudit>>> getAuditsByCorpNo(
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v2/rule-engine/audits - corpNo={}", corpNo);
        return ResponseEntity.ok(
                ApiResponse.<List<ComplianceAudit>>builder()
                        .payload(ruleEngineV2Service.listAuditsByCorpNo(corpNo))
                        .message("Fetched compliance audits successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/audits/{id}")
    @Operation(
            summary = "Get a compliance audit by id",
            description = "Returns a single persisted compliance audit by its audit id."
    )
    public ResponseEntity<ApiResponse<ComplianceAudit>> getAuditById(@PathVariable("id") String id) {
        log.info("GET /api/v2/rule-engine/audits/{}", id);
        return ResponseEntity.ok(
                ApiResponse.<ComplianceAudit>builder()
                        .payload(ruleEngineV2Service.getAuditById(id))
                        .message("Fetched compliance audit successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PutMapping("/audits/{id}")
    @Operation(
            summary = "Update a compliance audit's verdict by id",
            description = "Analyst override of complianceStatus (NORMAL|SUSPICION) and/or confidenceLevel "
                    + "(HIGH|MEDIUM|LOW). A null field is left unchanged."
    )
    public ResponseEntity<ApiResponse<ComplianceAudit>> updateAudit(
            @PathVariable("id") String id,
            @RequestBody ComplianceAuditUpdateRequest request) {
        log.info("PUT /api/v2/rule-engine/audits/{} - complianceStatus={}, confidenceLevel={}",
                id, request.complianceStatus(), request.confidenceLevel());
        ComplianceAudit updated =
                ruleEngineV2Service.updateAudit(id, request.complianceStatus(), request.confidenceLevel());
        return ResponseEntity.ok(
                ApiResponse.<ComplianceAudit>builder()
                        .payload(updated)
                        .message("Updated compliance audit successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @DeleteMapping("/audits/batch")
    @Operation(
            summary = "Delete multiple compliance audits by id",
            description = "Deletes the given audit ids. Invalid or unknown ids are skipped; the response "
                    + "reports how many were requested vs. actually deleted."
    )
    public ResponseEntity<ApiResponse<ComplianceAuditBatchDeleteResponse>> deleteBatch(
            @RequestBody ComplianceAuditBatchDeleteRequest request) {
        log.info("DELETE /api/v2/rule-engine/audits/batch - {} id(s)",
                request.ids() == null ? 0 : request.ids().size());
        return ResponseEntity.ok(
                ApiResponse.<ComplianceAuditBatchDeleteResponse>builder()
                        .payload(ruleEngineV2Service.deleteAudits(request.ids()))
                        .message("Deleted compliance audits successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @DeleteMapping("/audits/{id}")
    @Operation(
            summary = "Delete a compliance audit by id",
            description = "Deletes a single persisted compliance audit by its audit id."
    )
    public ResponseEntity<ApiResponse<String>> deleteAudit(@PathVariable("id") String id) {
        log.info("DELETE /api/v2/rule-engine/audits/{}", id);
        ruleEngineV2Service.deleteAudit(id);
        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .payload("Deleted compliance audit " + id)
                        .message("Deleted compliance audit successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }
}
