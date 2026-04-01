package com.api.bizplay_classifier_api.service.categoryService;

import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.response.CategoryUploadSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    CategoryDTO createCategory(CategoryRequest categoryRequest);

    List<CategoryDTO> getAllCategories();

    List<CategoryDTO> getAllCategoriesByCompanyId(UUID companyId);

    CategoryUploadSummaryResponse createCategoriesByExcel(MultipartFile file, UUID companyId, String sheetName);
}
