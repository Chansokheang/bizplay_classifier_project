package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.StaffRequest;
import com.api.bizplay_conversational.model.response.StaffResponse;
import com.api.bizplay_conversational.service.staffService.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@Slf4j
@Tag(name = "Staff", description = "Manage conversational staff")
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @Operation(summary = "List staff by corpNo")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getByCorpNo(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/staff - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(staffService.getByCorpNo(corpNo)));
    }

    @Operation(summary = "Get a staff member by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffResponse>> getById(@PathVariable("id") String id) {
        log.info("GET /api/v1/staff/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(staffService.getById(id)));
    }

    @Operation(summary = "Create a staff member")
    @PostMapping
    public ResponseEntity<ApiResponse<StaffResponse>> create(@Valid @RequestBody StaffRequest request) {
        log.info("POST /api/v1/staff - departmentId={}, name={}", request.getDepartmentId(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(staffService.create(request)));
    }

    @Operation(summary = "Update a staff member by id")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffResponse>> update(
            @PathVariable("id") String id, @Valid @RequestBody StaffRequest request) {
        log.info("PUT /api/v1/staff/{} - name={}", id, request.getName());
        return ResponseEntity.ok(ApiResponse.ok(staffService.update(id, request)));
    }

    @Operation(summary = "Delete a staff member by id")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteById(@PathVariable("id") String id) {
        log.info("DELETE /api/v1/staff/{}", id);
        staffService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted staff " + id));
    }
}
