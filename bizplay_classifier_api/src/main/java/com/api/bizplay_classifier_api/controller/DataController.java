package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.DataTrainSummaryResponse;
import com.api.bizplay_classifier_api.service.ruleService.RuleService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/data")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = {"http://localhost:3000"})
public class DataController {

    private final RuleService ruleService;

    @PostMapping(value = "/train", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> trainRulesFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("companyId") UUID companyId,
            @RequestParam(value = "sheetName", required = false) String sheetName
    ) {
        DataTrainSummaryResponse payload = ruleService.trainRulesFromExcel(file, companyId, sheetName);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<DataTrainSummaryResponse>builder()
                        .payload(payload)
                        .message("Training completed successfully.")
                        .status(HttpStatus.CREATED)
                        .code(HttpStatus.CREATED.value())
                        .build()
        );
    }
}

