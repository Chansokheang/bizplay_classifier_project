package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CategoryRepo {

    @Select("""
        INSERT INTO categories (company_id, code, category)
        VALUES (#{category.companyId}, #{category.code}, #{category.category})
        RETURNING *
    """)
    @Results(id = "categoryMap", value = {
            @Result(property = "categoryId", column = "category_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyId", column = "company_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "code", column = "code"),
            @Result(property = "category", column = "category"),
            @Result(property = "isUsed", column = "사용여부")
    })
    CategoryDTO createCategory(@Param("category") CategoryRequest categoryRequest);

    @Select("""
        SELECT * FROM categories ORDER BY category
    """)
    @ResultMap("categoryMap")
    List<CategoryDTO> getAllCategories();

    @Select("""
        SELECT * FROM categories
        WHERE company_id = #{companyId}
        ORDER BY category
    """)
    @ResultMap("categoryMap")
    List<CategoryDTO> getAllCategoriesByCompanyId(@Param("companyId") UUID companyId);

    @Select("""
        SELECT *
        FROM categories
        WHERE company_id = #{companyId}
          AND code = #{code}
        LIMIT 1
    """)
    @ResultMap("categoryMap")
    CategoryDTO findByCompanyIdAndCode(@Param("companyId") UUID companyId, @Param("code") String code);

    @Select("""
        SELECT c.*
        FROM categories c INNER JOIN rule_category_map rc ON c.category_id = rc.category_id
        WHERE rule_id = #{ruleId}
   """)
    @ResultMap("categoryMap")
    CategoryDTO getAllCategoriesByRuleId(@Param("ruleId") UUID ruleId);

    @Select("""
        SELECT *
        FROM categories
        WHERE company_id = #{companyId}
          AND category = #{category}
        LIMIT 1
    """)
    @ResultMap("categoryMap")
    CategoryDTO findByCompanyIdAndCategory(@Param("companyId") UUID companyId, @Param("category") String category);

    @Select("""
        SELECT COUNT(1)
        FROM companies
        WHERE company_id = #{companyId}
          AND user_id = #{userId}
    """)
    int existsCompanyByIdAndUserId(@Param("companyId") UUID companyId, @Param("userId") UUID userId);

    @Update("""
        UPDATE categories
        SET "사용여부" = TRUE
        WHERE category_id = #{categoryId}
    """)
    Integer markCategoryAsUsed(@Param("categoryId") UUID categoryId);
}
