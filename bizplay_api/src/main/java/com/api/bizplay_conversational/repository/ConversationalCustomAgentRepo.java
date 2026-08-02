package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.ConversationalCustomAgent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** CRUD for user-defined sub-agents ({@code conversational_custom_agent}), keyed (corp_no, name). */
@Mapper
public interface ConversationalCustomAgentRepo {

    @Results(id = "customAgent", value = {
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "prompt", column = "prompt"),
            @Result(property = "model", column = "model"),
            @Result(property = "tools", column = "tools"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    @Select("SELECT * FROM conversational_custom_agent WHERE corp_no = #{corpNo} ORDER BY name")
    List<ConversationalCustomAgent> findAll(@Param("corpNo") String corpNo);

    @ResultMap("customAgent")
    @Select("SELECT * FROM conversational_custom_agent WHERE corp_no = #{corpNo} AND name = #{name}")
    ConversationalCustomAgent findByName(@Param("corpNo") String corpNo, @Param("name") String name);

    @Insert("""
            INSERT INTO conversational_custom_agent
                (corp_no, name, description, prompt, model, tools, enabled, created_date, updated_date)
            VALUES
                (#{corpNo}, #{name}, #{description}, #{prompt}, #{model}, #{tools}, #{enabled}, NOW(), NOW())
            ON CONFLICT (corp_no, name) DO UPDATE SET
                description = EXCLUDED.description,
                prompt = EXCLUDED.prompt,
                model = EXCLUDED.model,
                tools = EXCLUDED.tools,
                enabled = EXCLUDED.enabled,
                updated_date = NOW()
            """)
    void upsert(ConversationalCustomAgent a);

    @Delete("DELETE FROM conversational_custom_agent WHERE corp_no = #{corpNo} AND name = #{name}")
    int deleteByName(@Param("corpNo") String corpNo, @Param("name") String name);
}
