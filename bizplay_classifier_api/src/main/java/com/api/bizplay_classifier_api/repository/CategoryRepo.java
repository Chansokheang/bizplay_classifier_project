package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.CategoryDTO;
import com.api.bizplay_classifier_api.model.request.CategoryRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CategoryRepo {

    @Select("""
        INSERT INTO classifier_categories (corp_no, code, category)
        VALUES (#{category.corpNo}, #{category.code}, #{category.category})
        RETURNING *
    """)
    @Results(id = "categoryMap", value = {
            @Result(property = "categoryId", column = "category_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "code", column = "code"),
            @Result(property = "category", column = "category"),
            @Result(property = "isUsed", column = "사용여부")
    })
    CategoryDTO createCategory(@Param("category") CategoryRequest categoryRequest);

    @Select("""
        SELECT * FROM classifier_categories ORDER BY category
    """)
    @ResultMap("categoryMap")
    List<CategoryDTO> getAllCategories();

    @Select("""
        SELECT * FROM classifier_categories
        WHERE corp_no = #{corpNo}
        ORDER BY category
    """)
    @ResultMap("categoryMap")
    List<CategoryDTO> getAllCategoriesByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        SELECT *
        FROM classifier_categories
        WHERE corp_no = #{corpNo}
          AND code = #{code}
        LIMIT 1
    """)
    @ResultMap("categoryMap")
    CategoryDTO findByCorpNoAndCode(@Param("corpNo") String corpNo, @Param("code") String code);

    @Select({
            "<script>",
            "SELECT *",
            "FROM classifier_categories",
            "WHERE corp_no = #{corpNo}",
            "  AND code IN",
            "  <foreach collection='codes' item='code' open='(' separator=',' close=')'>",
            "    #{code}",
            "  </foreach>",
            "</script>"
    })
    @ResultMap("categoryMap")
    List<CategoryDTO> findByCorpNoAndCodes(@Param("corpNo") String corpNo, @Param("codes") List<String> codes);

    @Select("""
        SELECT c.*
        FROM classifier_categories c
        INNER JOIN rule_category_map rc ON c.code = rc.code
        WHERE rule_id = #{ruleId}
    """)
    @ResultMap("categoryMap")
    List<CategoryDTO> getAllCategoriesByRuleId(@Param("ruleId") UUID ruleId);

    @Select("""
        SELECT *
        FROM classifier_categories
        WHERE corp_no = #{corpNo}
          AND category = #{category}
        LIMIT 1
    """)
    @ResultMap("categoryMap")
    CategoryDTO findByCorpNoAndCategory(@Param("corpNo") String corpNo, @Param("category") String category);

    @Select("""
        SELECT COUNT(1)
        FROM corp
        WHERE corp_no = #{corpNo}
          AND user_id = #{userId}
    """)
    int existsCorpByCorpNoAndUserId(@Param("corpNo") String corpNo, @Param("userId") UUID userId);

    @Update("""
        UPDATE classifier_categories
        SET 사용여부 = TRUE
        WHERE category_id = #{categoryId}
    """)
    Integer markCategoryAsUsed(@Param("categoryId") UUID categoryId);
}
