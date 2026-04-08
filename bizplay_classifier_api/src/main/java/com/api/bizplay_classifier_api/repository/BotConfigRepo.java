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
        INSERT INTO bot_config (company_id, config)
        VALUES (#{bot.companyId}, CAST(#{configJson} AS json))
        RETURNING *
    """)
    @Results(id = "botConfigMap", value = {
            @Result(property = "botId", column = "bot_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyId", column = "company_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "config", column = "config"),
            @Result(property = "createdDate", column = "created_date")
    })
    BotConfigDTO createBotConfig(@Param("bot") BotConfigRequest botConfigRequest, @Param("configJson") String configJson);

    @Select("""
        SELECT bot_id
        FROM bot_config
        WHERE company_id = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    UUID findLatestBotIdByCompanyId(@Param("companyId") UUID companyId);

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
        WHERE company_id = #{companyId}
          AND user_id = #{userId}
    """)
    int existsCompanyByIdAndUserId(@Param("companyId") UUID companyId, @Param("userId") UUID userId);

    @Select("""
        SELECT config ->> 'systemPrompt'
        FROM bot_config
        WHERE company_id = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    String getLatestSystemPromptByCompanyId(@Param("companyId") UUID companyId);

    @Select("""
        SELECT config::text
        FROM bot_config
        WHERE company_id = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    String getLatestConfigJsonByCompanyId(@Param("companyId") UUID companyId);

    @Select("""
        SELECT *
        FROM bot_config
        WHERE company_id = #{companyId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    @ResultMap("botConfigMap")
    BotConfigDTO getLatestBotConfigByCompanyId(@Param("companyId") UUID companyId);
}
