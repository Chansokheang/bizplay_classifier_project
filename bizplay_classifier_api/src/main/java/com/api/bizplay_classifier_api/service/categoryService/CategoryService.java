package com.api.bizplay_classifier_api.service.categoryService;

import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.response.CategoryUploadSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
public interface CategoryService {
    CategoryDTO createCategory(CategoryRequest categoryRequest);

    List<CategoryDTO> getAllCategories();

    List<CategoryDTO> getAllCategoriesByCompanyId(String companyId);

    CategoryUploadSummaryResponse createCategoriesByExcel(MultipartFile file, String companyId, String sheetName);
}
