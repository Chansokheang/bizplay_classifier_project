package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.TransactionDTO;
import com.api.bizplay_classifier_api.model.request.TransactionRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

@Mapper
public interface TransactionRepo {

    @Select("""
        INSERT INTO transactions (
            company_business_number,
            approval_date,
            approval_time,
            merchant_name,
            merchant_industry_code,
            merchant_industry_name,
            merchant_business_reg_number,
            supply_amount,
            vat_amount,
            tax_type,
            field_name1,
            pk
        )
        VALUES (
            #{transaction.companyId},
            #{transaction.approvalDate},
            #{transaction.approvalTime},
            #{transaction.merchantName},
            #{transaction.merchantIndustryCode},
            #{transaction.merchantIndustryName},
            #{transaction.merchantBusinessRegistrationNumber},
            #{transaction.supplyAmount},
            #{transaction.vatAmount},
            #{transaction.taxType},
            #{transaction.fieldName1},
            #{transaction.pk}
        )
        RETURNING *
    """)
    @Results(id = "transactionMap", value = {
            @Result(property = "transactionId", column = "transaction_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyId", column = "company_business_number"),
            @Result(property = "approvalDate", column = "approval_date"),
            @Result(property = "approvalTime", column = "approval_time"),
            @Result(property = "merchantName", column = "merchant_name"),
            @Result(property = "merchantIndustryCode", column = "merchant_industry_code"),
            @Result(property = "merchantIndustryName", column = "merchant_industry_name"),
            @Result(property = "merchantBusinessRegistrationNumber", column = "merchant_business_reg_number"),
            @Result(property = "supplyAmount", column = "supply_amount"),
            @Result(property = "vatAmount", column = "vat_amount"),
            @Result(property = "taxType", column = "tax_type"),
            @Result(property = "fieldName1", column = "field_name1"),
            @Result(property = "pk", column = "pk"),
            @Result(property = "createdDate", column = "created_date")
    })
    TransactionDTO createTransaction(@Param("transaction") TransactionRequest transactionRequest);

    @Insert({
            "<script>",
            "INSERT INTO transactions (",
            "company_business_number, approval_date, approval_time, merchant_name,",
            "merchant_industry_code, merchant_industry_name, merchant_business_reg_number,",
            "supply_amount, vat_amount, tax_type, field_name1, pk",
            ") VALUES",
            "<foreach collection='transactions' item='t' separator=','>",
            "(",
            "#{t.companyId}, #{t.approvalDate}, #{t.approvalTime}, #{t.merchantName},",
            "#{t.merchantIndustryCode}, #{t.merchantIndustryName}, #{t.merchantBusinessRegistrationNumber},",
            "#{t.supplyAmount}, #{t.vatAmount}, #{t.taxType}, #{t.fieldName1}, #{t.pk}",
            ")",
            "</foreach>",
            "</script>"
    })
    int createTransactionsBatch(@Param("transactions") java.util.List<TransactionRequest> transactions);
}
