package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.ConversationalMcpServer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** CRUD for corp-registered MCP servers ({@code conversational_mcp_server}), keyed (corp_no, name). */
@Mapper
public interface ConversationalMcpServerRepo {

    @Results(id = "mcpServer", value = {
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "name", column = "name"),
            @Result(property = "url", column = "url"),
            @Result(property = "authHeader", column = "auth_header"),
            @Result(property = "trusted", column = "trusted"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    @Select("SELECT * FROM conversational_mcp_server WHERE corp_no = #{corpNo} ORDER BY name")
    List<ConversationalMcpServer> findAll(@Param("corpNo") String corpNo);

    @ResultMap("mcpServer")
    @Select("SELECT * FROM conversational_mcp_server WHERE corp_no = #{corpNo} AND name = #{name}")
    ConversationalMcpServer findByName(@Param("corpNo") String corpNo, @Param("name") String name);

    @Insert("""
            INSERT INTO conversational_mcp_server
                (corp_no, name, url, auth_header, trusted, enabled, created_date, updated_date)
            VALUES
                (#{corpNo}, #{name}, #{url}, #{authHeader}, #{trusted}, #{enabled}, NOW(), NOW())
            ON CONFLICT (corp_no, name) DO UPDATE SET
                url = EXCLUDED.url,
                auth_header = EXCLUDED.auth_header,
                trusted = EXCLUDED.trusted,
                enabled = EXCLUDED.enabled,
                updated_date = NOW()
            """)
    void upsert(ConversationalMcpServer s);

    @Delete("DELETE FROM conversational_mcp_server WHERE corp_no = #{corpNo} AND name = #{name}")
    int deleteByName(@Param("corpNo") String corpNo, @Param("name") String name);
}
