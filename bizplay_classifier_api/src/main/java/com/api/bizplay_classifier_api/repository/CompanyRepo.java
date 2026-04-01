package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.CompanyDTO;
import com.api.bizplay_classifier_api.model.request.CompanyRequest;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CompanyRepo {

    @Select("""
        SELECT * FROM companies WHERE user_id = #{userId}
    """)
    @ResultMap("companyMap")
    List<CompanyDTO> getAllCompanyByUserId(@Param("userId") UUID userId);

    @Select("""
        INSERT INTO companies (user_id, company_name, business_number) VALUES (#{userId}, #{company.companyName}, #{company.businessNumber}) RETURNING *
    """)
    @ResultMap("companyMap")
    CompanyDTO createCompany(@Param("company") CompanyRequest companyRequest, @Param("userId") UUID userId);

    @Select("""
        SELECT * FROM companies WHERE company_id = #{companyId} AND user_id = #{userId}
    """)
    @Results(id = "companyMap", value = {
            @Result(property = "companyId",    column = "company_id",   jdbcType = JdbcType.OTHER,   typeHandler = UUIDTypeHandler.class),
            @Result(property = "userId",    column = "user_id",   jdbcType = JdbcType.OTHER,   typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyName",    column = "company_name"),
            @Result(property = "businessNumber",    column = "business_number"),
            @Result(property = "createdDate",    column = "created_date"),
            @Result(property = "ruleDTOList", column = "company_id", many = @Many(select = "com.api.bizplay_classifier_api.repository.RuleRepo.getAllRulesByCompanyId"))
    })
    CompanyDTO getCompanyByCompanyId(@Param("userId") UUID userId, @Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM classify_rule
        WHERE rule_id IN (
            SELECT rule_id FROM rules WHERE company_id = #{companyId}
        )
           OR classify_id IN (
            SELECT cd.classify_id
            FROM classify_detail cd
            INNER JOIN transactions t ON t.transaction_id = cd.transaction_id
            WHERE t.company_id = #{companyId}
        )
    """)
    int deleteClassifyRuleByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM classify_summary
        WHERE company_id = #{companyId}
    """)
    int deleteClassifySummaryByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM classify_detail
        WHERE transaction_id IN (
            SELECT transaction_id FROM transactions WHERE company_id = #{companyId}
        )
    """)
    int deleteClassifyDetailByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM transactions
        WHERE company_id = #{companyId}
    """)
    int deleteTransactionsByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM rule_category_map
        WHERE rule_id IN (
            SELECT rule_id FROM rules WHERE company_id = #{companyId}
        )
    """)
    int deleteRuleCategoryMapByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM rules
        WHERE company_id = #{companyId}
    """)
    int deleteRulesByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM categories
        WHERE company_id = #{companyId}
    """)
    int deleteCategoriesByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM bot_config
        WHERE company_id = #{companyId}
    """)
    int deleteBotConfigByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM file_upload_history
        WHERE company_id = #{companyId}
    """)
    int deleteFileUploadHistoryByCompanyId(@Param("companyId") UUID companyId);

    @Delete("""
        DELETE FROM companies
        WHERE company_id = #{companyId}
          AND user_id = #{userId}
    """)
    int deleteCompanyByCompanyId(@Param("userId") UUID userId, @Param("companyId") UUID companyId);
}
