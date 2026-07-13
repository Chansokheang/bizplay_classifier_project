package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.ConversationalLlmModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * CRUD for runtime-managed LLM model definitions ({@code conversational_llm_model}).
 */
@Mapper
public interface ConversationalLlmModelRepo {

    @Results(id = "llmModel", value = {
            @Result(property = "name", column = "name"),
            @Result(property = "label", column = "label"),
            @Result(property = "baseUrl", column = "base_url"),
            @Result(property = "apiKey", column = "api_key"),
            @Result(property = "authScheme", column = "auth_scheme"),
            @Result(property = "apiKeyHeader", column = "api_key_header"),
            @Result(property = "completionsPath", column = "completions_path"),
            @Result(property = "model", column = "model"),
            @Result(property = "temperature", column = "temperature"),
            @Result(property = "maxTokens", column = "max_tokens"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    @Select("SELECT * FROM conversational_llm_model ORDER BY created_date")
    List<ConversationalLlmModel> findAll();

    @ResultMap("llmModel")
    @Select("SELECT * FROM conversational_llm_model WHERE name = #{name}")
    ConversationalLlmModel findByName(@Param("name") String name);

    @Insert("""
            INSERT INTO conversational_llm_model
                (name, label, base_url, api_key, auth_scheme, api_key_header, completions_path,
                 model, temperature, max_tokens, enabled, created_date, updated_date)
            VALUES
                (#{name}, #{label}, #{baseUrl}, #{apiKey}, #{authScheme}, #{apiKeyHeader}, #{completionsPath},
                 #{model}, #{temperature}, #{maxTokens}, #{enabled}, NOW(), NOW())
            """)
    void insert(ConversationalLlmModel m);

    @Update("""
            UPDATE conversational_llm_model SET
                label = #{label},
                base_url = #{baseUrl},
                api_key = #{apiKey},
                auth_scheme = #{authScheme},
                api_key_header = #{apiKeyHeader},
                completions_path = #{completionsPath},
                model = #{model},
                temperature = #{temperature},
                max_tokens = #{maxTokens},
                enabled = #{enabled},
                updated_date = NOW()
            WHERE name = #{name}
            """)
    void update(ConversationalLlmModel m);

    @Delete("DELETE FROM conversational_llm_model WHERE name = #{name}")
    int deleteByName(@Param("name") String name);
}
