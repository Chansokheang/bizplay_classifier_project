package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.ConversationalAgentPrompt;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * CRUD for runtime-customized agent prompts ({@code conversational_agent_prompt}), scoped per
 * corporation: the key is (corp_no, name). A row overrides the agent's compiled-in default for
 * that corp; no row (or enabled = false) means the default applies.
 */
@Mapper
public interface ConversationalAgentPromptRepo {

    @Results(id = "agentPrompt", value = {
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "name", column = "name"),
            @Result(property = "prompt", column = "prompt"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    @Select("SELECT * FROM conversational_agent_prompt WHERE corp_no = #{corpNo} ORDER BY name")
    List<ConversationalAgentPrompt> findAll(@Param("corpNo") String corpNo);

    @ResultMap("agentPrompt")
    @Select("SELECT * FROM conversational_agent_prompt WHERE corp_no = #{corpNo} AND name = #{name}")
    ConversationalAgentPrompt findByName(@Param("corpNo") String corpNo, @Param("name") String name);

    @Insert("""
            INSERT INTO conversational_agent_prompt (corp_no, name, prompt, enabled, created_date, updated_date)
            VALUES (#{corpNo}, #{name}, #{prompt}, #{enabled}, NOW(), NOW())
            ON CONFLICT (corp_no, name) DO UPDATE SET
                prompt = EXCLUDED.prompt,
                enabled = EXCLUDED.enabled,
                updated_date = NOW()
            """)
    void upsert(ConversationalAgentPrompt p);

    @Delete("DELETE FROM conversational_agent_prompt WHERE corp_no = #{corpNo} AND name = #{name}")
    int deleteByName(@Param("corpNo") String corpNo, @Param("name") String name);
}
