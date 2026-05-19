package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.RuleClassifierDTO;
import com.api.bizplay_classifier_api.model.dto.RuleDTO;
import com.api.bizplay_classifier_api.model.request.RuleRequest;
import com.api.bizplay_classifier_api.model.request.RuleUpdateRequest;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
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
public interface RuleRepo {

    @Select("""
        INSERT INTO classifier_rules (
            corp_no,
            merchant_industry_name,
            merchant_industry_code,
            usage_status,
            min_amount,
            max_amount,
            description
        )
        VALUES (
            #{rule.corpNo},
            #{rule.merchantIndustryName},
            #{rule.merchantIndustryCode},
            'Y',
            #{rule.minAmount},
            #{rule.maxAmount},
            #{rule.description}
        )
        RETURNING *
    """)
    @Results(id = "ruleMap", value = {
            @Result(property = "ruleId", column = "rule_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "merchantIndustryName", column = "merchant_industry_name"),
            @Result(property = "merchantIndustryCode", column = "merchant_industry_code"),
            @Result(property = "usageStatus", column = "usage_status"),
            @Result(property = "minAmount", column = "min_amount"),
            @Result(property = "maxAmount", column = "max_amount"),
            @Result(property = "description", column = "description"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "categoryDTOList", column = "rule_id",
                    many = @Many(select = "com.api.bizplay_classifier_api.repository.CategoryRepo.getAllCategoriesByRuleId"))
    })
    RuleDTO createRule(@Param("rule") RuleRequest ruleRequest);

    @Select("""
        SELECT *
        FROM classifier_rules
        WHERE corp_no = #{corpNo}
        ORDER BY created_date DESC, rule_id DESC
    """)
    @ResultMap("ruleMap")
    List<RuleDTO> getAllRulesByCorpNo(@Param("corpNo") String corpNo);

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            "FROM classifier_rules",
            "WHERE corp_no = #{corpNo}",
            "<if test='usageStatus != null'>",
            "  AND usage_status = #{usageStatus}",
            "</if>",
            "</script>"
    })
    int countRulesByCorpNo(@Param("corpNo") String corpNo, @Param("usageStatus") String usageStatus);

    @Select({
            "<script>",
            "SELECT *",
            "FROM classifier_rules",
            "WHERE corp_no = #{corpNo}",
            "<if test='usageStatus != null'>",
            "  AND usage_status = #{usageStatus}",
            "</if>",
            "ORDER BY created_date DESC, rule_id DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    @ResultMap("ruleMap")
    List<RuleDTO> getRulesByCorpNoPaged(
            @Param("corpNo") String corpNo,
            @Param("usageStatus") String usageStatus,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    @Select("""
        SELECT *
        FROM classifier_rules
        WHERE corp_no = #{corpNo}
          AND merchant_industry_code = #{merchantIndustryCode}
        LIMIT 1
    """)
    @ResultMap("ruleMap")
    RuleDTO findByCorpNoAndIndustryCode(
            @Param("corpNo") String corpNo,
            @Param("merchantIndustryCode") String merchantIndustryCode
    );

    @Select("""
        SELECT
            r.merchant_industry_name AS merchant_industry_name,
            r.merchant_industry_code AS merchant_industry_code,
            r.description            AS description,
            c.code                   AS code,
            c.category               AS category
        FROM classifier_rules r
        JOIN rule_category_map rcm ON rcm.rule_id = r.rule_id
        JOIN classifier_categories c ON c.category_id = rcm.category_id
        WHERE r.corp_no = #{corpNo}
          AND c.is_used = TRUE
    """)
    @Results(id = "ruleClassifierMap", value = {
            @Result(property = "merchantIndustryName", column = "merchant_industry_name"),
            @Result(property = "merchantIndustryCode", column = "merchant_industry_code"),
            @Result(property = "description", column = "description"),
            @Result(property = "code", column = "code"),
            @Result(property = "category", column = "category")
    })
    List<RuleClassifierDTO> getRuleClassifiersByCorpNo(@Param("corpNo") String corpNo);

    @Update("""
        UPDATE classifier_rules
        SET usage_status = 'Y'
        WHERE corp_no = #{corpNo}
          AND merchant_industry_code = #{merchantIndustryCode}
          AND COALESCE(usage_status, '') <> 'Y'
    """)
    int markRulesAsUsedByCorpNoAndIndustryCode(
            @Param("corpNo") String corpNo,
            @Param("merchantIndustryCode") String merchantIndustryCode
    );

    @Select("""
        UPDATE classifier_rules
        SET
            merchant_industry_name = #{rule.merchantIndustryName},
            merchant_industry_code = #{rule.merchantIndustryCode},
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

    @Delete("""
        DELETE FROM classifier_rules WHERE rule_id = #{ruleId}
    """)
    Integer deleteRuleByRuleId(@Param("ruleId") UUID ruleId);

    @Delete("""
        DELETE FROM rule_category_map
        WHERE rule_id IN (
            SELECT rule_id
            FROM classifier_rules
            WHERE corp_no = #{corpNo}
        )
    """)
    Integer deleteRuleCategoryMappingsByCorpNo(@Param("corpNo") String corpNo);

    @Delete("""
        DELETE FROM classifier_rules
        WHERE corp_no = #{corpNo}
    """)
    Integer deleteRulesByCorpNo(@Param("corpNo") String corpNo);

    @Insert("""
        INSERT INTO rule_category_map (rule_id, category_id)
        VALUES (#{ruleId}, #{categoryId})
        ON CONFLICT DO NOTHING
    """)
    int createRuleCategoryMappingIgnoreConflict(@Param("ruleId") UUID ruleId, @Param("categoryId") UUID categoryId);

    default List<RuleDTO> getAllRulesByCompanyId(String companyId) {
        return getAllRulesByCorpNo(companyId);
    }

    default int countRulesByCompanyId(String companyId, String usageStatus) {
        return countRulesByCorpNo(companyId, usageStatus);
    }

    default List<RuleDTO> getRulesByCompanyIdPaged(String companyId, String usageStatus, int offset, int limit) {
        return getRulesByCorpNoPaged(companyId, usageStatus, offset, limit);
    }

    default RuleDTO findByCompanyIdAndIndustryCode(String companyId, String merchantIndustryCode) {
        return findByCorpNoAndIndustryCode(companyId, merchantIndustryCode);
    }

    default List<RuleClassifierDTO> getRuleClassifiersByCompanyId(String companyId) {
        return getRuleClassifiersByCorpNo(companyId);
    }

    default int markRulesAsUsedByCompanyIdAndIndustryCode(String companyId, String merchantIndustryCode) {
        return markRulesAsUsedByCorpNoAndIndustryCode(companyId, merchantIndustryCode);
    }
}
