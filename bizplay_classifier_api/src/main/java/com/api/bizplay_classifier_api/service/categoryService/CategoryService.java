package com.api.bizplay_classifier_api.service.categoryService;

import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.request.CategoryUpdateRequest;
import com.api.bizplay_classifier_api.model.response.CategoryUploadSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
public interface CategoryService {
    CategoryDTO createCategory(CategoryRequest categoryRequest);

    CategoryDTO updateCategory(String currentCode, CategoryUpdateRequest categoryUpdateRequest);

    List<CategoryDTO> getAllCategories();

    List<CategoryDTO> getAllCategoriesByCorpNo(String corpNo);

    CategoryUploadSummaryResponse createCategoriesByExcel(MultipartFile file, String corpNo, String sheetName);
}
