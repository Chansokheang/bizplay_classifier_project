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
        INSERT INTO classifier_bot_config (corp_no, config)
        VALUES (#{bot.corpNo}, CAST(#{configJson} AS json))
        RETURNING *
    """)
    @Results(id = "botConfigMap", value = {
            @Result(property = "botId", column = "bot_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "rawConfig", column = "config"),
            @Result(property = "createdDate", column = "created_date")
    })
    BotConfigDTO createBotConfig(@Param("bot") BotConfigRequest botConfigRequest, @Param("configJson") String configJson);

    @Select("""
        SELECT bot_id
        FROM classifier_bot_config
        WHERE corp_no = #{corpNo}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    UUID findLatestBotIdByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        UPDATE classifier_bot_config
        SET config = CAST(#{configJson} AS json)
        WHERE bot_id = #{botId}
        RETURNING *
    """)
    @ResultMap("botConfigMap")
    BotConfigDTO updateBotConfigByBotId(@Param("botId") UUID botId, @Param("configJson") String configJson);

    @Select("""
        SELECT COUNT(1)
        FROM corp
        WHERE corp_no = #{corpNo}
    """)
    int existsCorpByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        SELECT config ->> 'systemPrompt'
        FROM classifier_bot_config
        WHERE corp_no = #{corpNo}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    String getLatestSystemPromptByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        SELECT config::text
        FROM classifier_bot_config
        WHERE corp_no = #{corpNo}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    String getLatestConfigJsonByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        SELECT *
        FROM classifier_bot_config
        WHERE corp_no = #{corpNo}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    @ResultMap("botConfigMap")
    BotConfigDTO getLatestBotConfigByCorpNo(@Param("corpNo") String corpNo);

    default UUID findLatestBotIdByCompanyId(String companyId) {
        return findLatestBotIdByCorpNo(companyId);
    }

    default int existsCompanyById(String companyId) {
        return existsCorpByCorpNo(companyId);
    }

    default String getLatestSystemPromptByCompanyId(String companyId) {
        return getLatestSystemPromptByCorpNo(companyId);
    }

    default String getLatestConfigJsonByCompanyId(String companyId) {
        return getLatestConfigJsonByCorpNo(companyId);
    }

    default BotConfigDTO getLatestBotConfigByCompanyId(String companyId) {
        return getLatestBotConfigByCorpNo(companyId);
    }
}

