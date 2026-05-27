package com.api.bizplay_chatbot.corp.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_chatbot.corp.dto.CorpGroupCreateRequest;
import com.api.bizplay_chatbot.corp.dto.CorpGroupResponse;
import com.api.bizplay_chatbot.corp.dto.CorpGroupUpdateRequest;
import com.api.bizplay_chatbot.corp.service.CorpGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Corp Groups", description = "Manage corp_group rows (top of the tenant tree). Each corp belongs to exactly one corp_group.")
@RestController
@RequestMapping("/api/v1/corp-groups")
@RequiredArgsConstructor
public class CorpGroupController {

    private final CorpGroupService corpGroupService;

    @Operation(summary = "Create a corp group",
            description = "Creates a new corp_group with a unique corp_group_cd. Returns 409 on collision.")
    @PostMapping
    public ResponseEntity<ApiResponse<CorpGroupResponse>> create(@Valid @RequestBody CorpGroupCreateRequest req) {
        log.info("POST /chatbot/api/v1/corp-groups - cd={}", req.getCorpGroupCd());
        return ResponseEntity.ok(ApiResponse.ok(corpGroupService.create(req)));
    }

    @Operation(summary = "List corp groups", description = "Returns all corp groups, sorted by corp_group_cd.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CorpGroupResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(corpGroupService.list()));
    }

    @Operation(summary = "Get a corp group by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CorpGroupResponse>> get(
            @Parameter(description = "corp_group_id") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(corpGroupService.get(id)));
    }

    @Operation(summary = "Update a corp group",
            description = "PATCH semantics: omitted fields stay unchanged. Returns 409 if a new corp_group_cd collides with an existing row.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CorpGroupResponse>> update(
            @Parameter(description = "corp_group_id") @PathVariable Long id,
            @Valid @RequestBody CorpGroupUpdateRequest req) {
        log.info("PUT /chatbot/api/v1/corp-groups/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(corpGroupService.update(id, req)));
    }

    @Operation(summary = "Delete a corp group",
            description = "Returns 409 if any corp row still references the group — clear or move those corps first.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "corp_group_id") @PathVariable Long id) {
        log.info("DELETE /chatbot/api/v1/corp-groups/{}", id);
        corpGroupService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Corp group deleted", null));
    }
}
