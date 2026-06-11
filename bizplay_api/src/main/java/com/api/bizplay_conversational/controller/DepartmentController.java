package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.DepartmentRequest;
import com.api.bizplay_conversational.model.response.DepartmentResponse;
import com.api.bizplay_conversational.service.departmentService.DepartmentService;
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
@Tag(name = "Departments", description = "Manage conversational departments")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "List departments by corpNo")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getByCorpNo(@RequestParam("corpNo") String corpNo) {
        log.info("GET /api/v1/departments - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(departmentService.getByCorpNo(corpNo)));
    }

    @Operation(summary = "Get a department by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getById(@PathVariable("id") String id) {
        log.info("GET /api/v1/departments/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(departmentService.getById(id)));
    }

    @Operation(summary = "Create a department")
    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(@Valid @RequestBody DepartmentRequest request) {
        log.info("POST /api/v1/departments - corpNo={}, name={}", request.getCorpNo(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(departmentService.create(request)));
    }

    @Operation(summary = "Update a department by id")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @PathVariable("id") String id, @Valid @RequestBody DepartmentRequest request) {
        log.info("PUT /api/v1/departments/{} - name={}", id, request.getName());
        return ResponseEntity.ok(ApiResponse.ok(departmentService.update(id, request)));
    }

    @Operation(summary = "Delete a department by id")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteById(@PathVariable("id") String id) {
        log.info("DELETE /api/v1/departments/{}", id);
        departmentService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted department " + id));
    }
}
