package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.request.CompanyRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.CompanyResponse;
import com.api.bizplay_classifier_api.service.companyService.CompanyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Log4j2
@RestController
@RequestMapping("/api/v1/companies")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<?>> createCompany(@Valid @RequestBody CompanyRequest companyRequest) {
        return ResponseEntity.ok(
                ApiResponse.<CompanyResponse>builder()
                        .payload(companyService.createCompany(companyRequest))
                        .message("Company was created successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/allCompanies")
    public ResponseEntity<ApiResponse<?>> getAllCompany() throws Exception {
        List<CompanyResponse> companies = companyService.getAllCompanyByUserId();
        return ResponseEntity.ok(
                ApiResponse.<List<CompanyResponse>>builder()
                        .payload(companies)
                        .message("Get all companies successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<?>> getCompanyByCompanyId(@Valid @PathVariable String companyId) {
        return ResponseEntity.ok(
                ApiResponse.<CompanyResponse>builder()
                        .payload(companyService.getCompanyByCompanyId(companyId))
                        .message("Get company successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<ApiResponse<?>> deleteCompanyByCompanyId(@PathVariable String companyId) {
        companyService.deleteCompanyByCompanyId(companyId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Company was deleted successfully.")
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }
}
