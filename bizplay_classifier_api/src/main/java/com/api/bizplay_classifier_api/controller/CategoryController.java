package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.request.CategoryUpdateRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.CategoryUploadSummaryResponse;
import com.api.bizplay_classifier_api.service.categoryService.CategoryService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@AllArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<?>> createCategory(@Valid @RequestBody CategoryRequest categoryRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CategoryDTO>builder()
                        .payload(categoryService.createCategory(categoryRequest))
                        .message("Category processed successfully. Existing category is returned if duplicate.")
                        .code(HttpStatus.CREATED.value())
                        .status(HttpStatus.CREATED)
                        .build()
        );
    }

    @PutMapping("/update/{code}")
    public ResponseEntity<ApiResponse<?>> updateCategory(
            @PathVariable String code,
            @Valid @RequestBody CategoryUpdateRequest categoryUpdateRequest
    ) {
        return ResponseEntity.ok(
                ApiResponse.<CategoryDTO>builder()
                        .payload(categoryService.updateCategory(code, categoryUpdateRequest))
                        .message("Category was updated successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @GetMapping("/{corpNo}")
    public ResponseEntity<ApiResponse<?>> getAllCategoriesByCorpNo(@PathVariable String corpNo) {
        return ResponseEntity.ok(
                ApiResponse.<List<CategoryDTO>>builder()
                        .payload(categoryService.getAllCategoriesByCorpNo(corpNo))
                        .message("Categories were retrieved successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadCategoriesByExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "sheetName", required = false) String sheetName
    ) {
        CategoryUploadSummaryResponse payload = categoryService.createCategoriesByExcel(file, corpNo, sheetName);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CategoryUploadSummaryResponse>builder()
                        .payload(payload)
                        .message("Categories were created successfully from Excel.")
                        .code(HttpStatus.CREATED.value())
                        .status(HttpStatus.CREATED)
                        .build()
        );
    }
}

