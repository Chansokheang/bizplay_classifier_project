package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

import java.util.UUID;

@Mapper
public interface BotConfigRepo {

    @Select("""
        INSERT INTO bot_config (company_business_number, config)
        VALUES (#{bot.companyId}, CAST(#{configJson} AS json))
        RETURNING *
    """)
    @Results(id = "botConfigMap", value = {
            @Result(property = "botId", column = "bot_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyId", column = "company_business_number"),
            @Result(property = "rawConfig", column = "config"),
            @Result(property = "createdDate", column = "created_date")
    })
    BotConfigDTO createBotConfig(@Param("bot") BotConfigRequest botConfigRequest, @Param("configJson") String configJson);

    @Select("""
        SELECT bot_id
        FROM bot_config
        WHERE company_business_number = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    UUID findLatestBotIdByCompanyId(@Param("companyId") String companyId);

    @Select("""
        UPDATE bot_config
        SET config = CAST(#{configJson} AS json)
        WHERE bot_id = #{botId}
        RETURNING *
    """)
    @ResultMap("botConfigMap")
    BotConfigDTO updateBotConfigByBotId(@Param("botId") UUID botId, @Param("configJson") String configJson);

    @Select("""
        SELECT COUNT(1)
        FROM companies
        WHERE company_business_number = #{companyId}
          AND user_id = #{userId}
    """)
    int existsCompanyByIdAndUserId(@Param("companyId") String companyId, @Param("userId") UUID userId);

    @Select("""
        SELECT config ->> 'systemPrompt'
        FROM bot_config
        WHERE company_business_number = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    String getLatestSystemPromptByCompanyId(@Param("companyId") String companyId);

    @Select("""
        SELECT config::text
        FROM bot_config
        WHERE company_business_number = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    String getLatestConfigJsonByCompanyId(@Param("companyId") String companyId);

    @Select("""
        SELECT *
        FROM bot_config
        WHERE company_business_number = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    @ResultMap("botConfigMap")
    BotConfigDTO getLatestBotConfigByCompanyId(@Param("companyId") String companyId);
}
