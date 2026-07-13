package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.ConversationalLlmSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Persistence for the singleton {@link ConversationalLlmSetting} row (id = 1).
 */
@Mapper
public interface ConversationalLlmSettingRepo {

    @Select("SELECT id, active_model, updated_date FROM conversational_llm_setting WHERE id = 1")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "activeModel", column = "active_model"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    ConversationalLlmSetting findSingleton();

    /** Insert-or-update the singleton row with the given active model (null clears the override). */
    @Update("""
            INSERT INTO conversational_llm_setting (id, active_model, updated_date)
            VALUES (1, #{activeModel}, NOW())
            ON CONFLICT (id) DO UPDATE
                SET active_model = EXCLUDED.active_model,
                    updated_date = NOW()
            """)
    void upsertActiveModel(@Param("activeModel") String activeModel);
}
