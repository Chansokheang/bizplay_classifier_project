package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;
import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.RuleUpdateRequest;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

@Mapper
public interface RuleRepo {

    @Select("""
        INSERT INTO rules (company_id, rule_name, min_amount, max_amount, description)
        VALUES (#{rule.companyId}, #{rule.ruleName}, #{rule.minAmount}, #{rule.maxAmount}, #{rule.description})
        RETURNING *
    """)
    @Results(id = "ruleMap", value = {
            @Result(property = "ruleId", column = "rule_id",   jdbcType = JdbcType.OTHER,   typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyId", column = "company_id",   jdbcType = JdbcType.OTHER,   typeHandler = UUIDTypeHandler.class),
            @Result(property = "ruleName", column = "rule_name"),
            @Result(property = "usageStatus", column = "usage_status"),
            @Result(property = "minAmount", column = "min_amount"),
            @Result(property = "maxAmount", column = "max_amount"),
            @Result(property = "description", column = "description"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "categoryDTOList", column = "rule_id", many = @Many(select = "com.api.bizplay_classifier_api.repository.CategoryRepo.getAllCategoriesByRuleId"))
    })
    RuleDTO createRule(@Param("rule")RuleRequest ruleRequest);

    @Select("""
        SELECT * FROM rules WHERE company_id = #{companyId}
    """)
    @ResultMap("ruleMap")
    List<RuleDTO> getAllRulesByCompanyId(@Param("companyId")UUID companyId);

    @Select("""
        SELECT *
        FROM rules
        WHERE company_id = #{companyId}
          AND rule_name = #{ruleName}
        LIMIT 1
    """)
    @ResultMap("ruleMap")
    RuleDTO findByCompanyIdAndRuleName(@Param("companyId") UUID companyId, @Param("ruleName") String ruleName);

    @Select("""
        SELECT
            r.rule_name AS rule_name,
            c.code AS code,
            c.category AS category
        FROM rules r
        JOIN rule_category_map rcm ON rcm.rule_id = r.rule_id
        JOIN categories c ON c.category_id = rcm.category_id
        WHERE r.company_id = #{companyId}
    """)
    @Results(id = "ruleClassifierMap", value = {
            @Result(property = "ruleName", column = "rule_name"),
            @Result(property = "code", column = "code"),
            @Result(property = "category", column = "category")
    })
    List<RuleClassifierDTO> getRuleClassifiersByCompanyId(@Param("companyId") UUID companyId);

    @Select("""
        UPDATE rules
        SET
            rule_name = #{rule.ruleName},
            usage_status = COALESCE(#{rule.usageStatus}, usage_status),
            min_amount = #{rule.minAmount},
            max_amount = #{rule.maxAmount},
            description = #{rule.description}
        WHERE rule_id = #{ruleId}
        RETURNING *
    """)
    @ResultMap("ruleMap")
    RuleDTO updateRuleByRuleId(@Param("ruleId") UUID ruleId, @Param("rule") RuleUpdateRequest ruleUpdateRequest);

    @Insert({
            "<script>",
            "INSERT INTO rule_category_map (rule_id, category_id) VALUES",
            "<foreach collection='categoryIds' item='categoryId' separator=','>",
            "(#{ruleId}, #{categoryId})",
            "</foreach>",
            "</script>"
    })
    Integer createRuleCategoryMappings(@Param("ruleId") UUID ruleId, @Param("categoryIds") List<UUID> categoryIds);

    @Delete("""
        DELETE FROM rule_category_map WHERE rule_id = #{ruleId}
    """)
    Integer deleteRuleCategoryMappings(@Param("ruleId") UUID ruleId);

    @Insert("""
        INSERT INTO rule_category_map (rule_id, category_id)
        VALUES (#{ruleId}, #{categoryId})
        ON CONFLICT DO NOTHING
    """)
    int createRuleCategoryMappingIgnoreConflict(@Param("ruleId") UUID ruleId, @Param("categoryId") UUID categoryId);

}
