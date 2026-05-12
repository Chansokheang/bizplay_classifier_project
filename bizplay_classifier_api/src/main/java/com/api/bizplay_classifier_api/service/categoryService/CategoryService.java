package com.api.bizplay_classifier_api.service.categoryService;

import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryBatchItemRequest;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import com.api.bizplay_classifier_api.model.request.CategoryUpdateRequest;
import com.api.bizplay_classifier_api.model.response.CategoryUploadPayloadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
public interface CategoryService {
    CategoryDTO createCategory(CategoryRequest categoryRequest);

    List<CategoryDTO> createCategories(String corpNo, List<CategoryBatchItemRequest> categoryRequests);

    CategoryDTO updateCategory(String currentCode, CategoryUpdateRequest categoryUpdateRequest);

    List<CategoryDTO> updateCategories(String corpNo, List<CategoryBatchItemRequest> categoryRequests);

    List<CategoryDTO> getAllCategories();

    List<CategoryDTO> getAllCategoriesByCorpNo(String corpNo);

    List<CategoryUploadPayloadResponse> createCategoriesByExcel(MultipartFile file, String corpNo, String sheetName);
}
