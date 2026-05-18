package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.request.CorpRequest;
import com.api.bizplay_classifier_api.model.request.CorpGroupRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.CorpResponse;
import com.api.bizplay_classifier_api.model.response.CorpGroupResponse;
import com.api.bizplay_classifier_api.service.corpService.CorpService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Log4j2
@RestController
@RequestMapping("/api/v1/corps")
@AllArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class CorpController {

    private final CorpService corpService;

    @PostMapping("/corp-groups")
    public ResponseEntity<ApiResponse<?>> createCorpGroup(@Valid @RequestBody CorpGroupRequest corpGroupRequest) {
        return ResponseEntity.ok(
                ApiResponse.<CorpGroupResponse>builder()
                        .payload(corpService.createCorpGroup(corpGroupRequest))
                        .message("Corp group was created successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/corp-groups")
    public ResponseEntity<ApiResponse<?>> getAllCorpGroups() {
        return ResponseEntity.ok(
                ApiResponse.<List<CorpGroupResponse>>builder()
                        .payload(corpService.getAllCorpGroups())
                        .message("Get all corp groups successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createCorp(@Valid @RequestBody CorpRequest corpRequest) {
        return ResponseEntity.ok(
                ApiResponse.<CorpResponse>builder()
                        .payload(corpService.createCorp(corpRequest))
                        .message("Corp was created successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllCorps() throws Exception {
        List<CorpResponse> corps = corpService.getAllCorps();
        return ResponseEntity.ok(
                ApiResponse.<List<CorpResponse>>builder()
                        .payload(corps)
                        .message("Get all corps successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/corp-groups/{corpGroupCode}/corps")
    public ResponseEntity<ApiResponse<?>> getAllCorpsByCorpGroupCode(@PathVariable String corpGroupCode) {
        return ResponseEntity.ok(
                ApiResponse.<List<CorpResponse>>builder()
                        .payload(corpService.getAllCorpsByCorpGroupCode(corpGroupCode))
                        .message("Get corps by corp group code successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/{corpNo}")
    public ResponseEntity<ApiResponse<?>> getCorpByCorpNo(@Valid @PathVariable String corpNo) {
        return ResponseEntity.ok(
                ApiResponse.<CorpResponse>builder()
                        .payload(corpService.getCorpByCorpNo(corpNo))
                        .message("Get corp successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @DeleteMapping("/{corpNo}")
    public ResponseEntity<ApiResponse<?>> deleteCorpByCorpNo(@PathVariable String corpNo) {
        corpService.deleteCorpByCorpNo(corpNo);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Corp was deleted successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }
}
