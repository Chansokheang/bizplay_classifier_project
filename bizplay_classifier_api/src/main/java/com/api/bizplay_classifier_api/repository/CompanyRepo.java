package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.model.dto.CompanyDTO;
import com.api.bizplay_classifier_api.model.request.CompanyRequest;
import org.apache.ibatis.annotations.*;
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
        INSERT INTO companies (user_id, company_name, company_business_number)
        VALUES (#{userId}, #{company.companyName}, #{company.businessNumber})
        RETURNING *
    """)
    @ResultMap("companyMap")
    CompanyDTO createCompany(@Param("company") CompanyRequest companyRequest, @Param("userId") UUID userId);

    @Select("""
        SELECT * FROM companies WHERE company_business_number = #{companyId} AND user_id = #{userId}
    """)
    @Results(id = "companyMap", value = {
            @Result(property = "companyId",    column = "company_business_number"),
            @Result(property = "businessNumber",    column = "company_business_number"),
            @Result(property = "userId",    column = "user_id"),
            @Result(property = "companyName",    column = "company_name"),
            @Result(property = "createdDate",    column = "created_date"),
            @Result(property = "ruleDTOList", column = "company_business_number", many = @Many(select = "com.api.bizplay_classifier_api.repository.RuleRepo.getAllRulesByCompanyId"))
    })
    CompanyDTO getCompanyByCompanyId(@Param("userId") UUID userId, @Param("companyId") String companyId);

    @Delete("""
        DELETE FROM classify_rule
        WHERE rule_id IN (
            SELECT rule_id FROM rules WHERE company_business_number = #{companyId}
        )
           OR classify_id IN (
            SELECT cd.classify_id
            FROM classify_detail cd
            INNER JOIN transactions t ON t.transaction_id = cd.transaction_id
            WHERE t.company_business_number = #{companyId}
        )
    """)
    int deleteClassifyRuleByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM classify_summary
        WHERE company_business_number = #{companyId}
    """)
    int deleteClassifySummaryByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM classify_detail
        WHERE transaction_id IN (
            SELECT transaction_id FROM transactions WHERE company_business_number = #{companyId}
        )
    """)
    int deleteClassifyDetailByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM transactions
        WHERE company_business_number = #{companyId}
    """)
    int deleteTransactionsByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM rule_category_map
        WHERE rule_id IN (
            SELECT rule_id FROM rules WHERE company_business_number = #{companyId}
        )
    """)
    int deleteRuleCategoryMapByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM rules
        WHERE company_business_number = #{companyId}
    """)
    int deleteRulesByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM categories
        WHERE company_business_number = #{companyId}
    """)
    int deleteCategoriesByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM bot_config
        WHERE company_business_number = #{companyId}
    """)
    int deleteBotConfigByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM file_upload_history
        WHERE company_business_number = #{companyId}
    """)
    int deleteFileUploadHistoryByCompanyId(@Param("companyId") String companyId);

    @Delete("""
        DELETE FROM companies
        WHERE company_business_number = #{companyId}
          AND user_id = #{userId}
    """)
    int deleteCompanyByCompanyId(@Param("userId") UUID userId, @Param("companyId") String companyId);
}
