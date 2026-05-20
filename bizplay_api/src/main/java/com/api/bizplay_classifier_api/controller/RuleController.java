package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.RuleUpdateRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.RulePageResponse;
import com.api.bizplay_classifier_api.service.ruleService.RuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Log4j2
@RestController
@RequestMapping("/classifier/api/v1/rules")
@Tag(
        name = "Rules",
        description = "Rule management endpoints for creating, updating, deleting, and listing transaction classification rules."
)
@AllArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class RuleController {

    private final RuleService ruleService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<?>> createRule(@Valid @RequestBody RuleRequest ruleRequest) {
        return ResponseEntity.ok(
                ApiResponse.<RuleDTO>builder()
                        .payload(ruleService.createRule(ruleRequest))
                        .message("Rule was created successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PutMapping("/update/{ruleId}")
    public ResponseEntity<ApiResponse<?>> updateRuleByRuleId(@PathVariable UUID ruleId, @Valid @RequestBody RuleUpdateRequest ruleUpdateRequest) {
        return ResponseEntity.ok(
                ApiResponse.<RuleDTO>builder()
                        .payload(ruleService.updateRuleByRuleId(ruleId, ruleUpdateRequest))
                        .message("Rule was updated successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<?>> deleteRuleByRuleId(@PathVariable UUID ruleId) {
        ruleService.deleteRuleByRuleId(ruleId);
        return ResponseEntity.ok(
                ApiResponse.<Object>builder()
                        .payload(null)
                        .message("Rule was deleted successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @DeleteMapping("/corp/{corpNo}")
    public ResponseEntity<ApiResponse<?>> deleteRulesByCorpNo(@PathVariable String corpNo) {
        ruleService.deleteRulesByCorpNo(corpNo);
        return ResponseEntity.ok(
                ApiResponse.<Object>builder()
                        .payload(null)
                        .message("All rules were deleted successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/{corpNo}")
    public ResponseEntity<ApiResponse<?>> getAllRulesByCorpNo(
            @PathVariable String corpNo,
            @RequestParam(value = "usageStatus", required = false) String usageStatus,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(
                ApiResponse.<RulePageResponse>builder()
                        .payload(ruleService.getAllRulesByCompanyId(corpNo, usageStatus, page, limit))
                        .message("Rules was retrieved successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }
}

