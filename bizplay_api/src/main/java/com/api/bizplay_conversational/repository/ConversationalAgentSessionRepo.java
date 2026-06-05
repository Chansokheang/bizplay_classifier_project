package com.api.bizplay_conversational.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_conversational.config.JsonNodeTypeHandler;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ConversationalAgentSessionRepo {

    @Select("""
            SELECT id,
                   corp_no,
                   agent_type,
                   status,
                   draft_json,
                   chat_event_json,
                   created_date,
                   updated_date
            FROM conversational_agent_session
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    @Results(id = "agentSessionResultMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "agentType", column = "agent_type"),
            @Result(property = "status", column = "status"),
            @Result(property = "draftJson", column = "draft_json", jdbcType = JdbcType.OTHER, typeHandler = JsonNodeTypeHandler.class),
            @Result(property = "chatEventJson", column = "chat_event_json", jdbcType = JdbcType.OTHER, typeHandler = JsonNodeTypeHandler.class),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    ConversationalAgentSession findOneById(@Param("id") UUID id);

    default Optional<ConversationalAgentSession> findById(UUID id) {
        return Optional.ofNullable(findOneById(id));
    }

    /**
     * List sessions for a corp, newest first. Excludes the heavy JSON columns so the result
     * stays lightweight for browsing/finding a session to reuse.
     */
    @Select("""
            SELECT id,
                   corp_no,
                   agent_type,
                   status,
                   created_date,
                   updated_date
            FROM conversational_agent_session
            WHERE corp_no = #{corpNo}
            ORDER BY updated_date DESC
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "agentType", column = "agent_type"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    List<ConversationalAgentSession> findByCorpNo(@Param("corpNo") String corpNo);

    @Insert("""
            INSERT INTO conversational_agent_session (
                id,
                corp_no,
                agent_type,
                status,
                draft_json,
                chat_event_json
            )
            VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{corpNo},
                #{agentType},
                #{status},
                #{draftJson, jdbcType=OTHER, typeHandler=com.api.bizplay_conversational.config.JsonNodeTypeHandler},
                #{chatEventJson, jdbcType=OTHER, typeHandler=com.api.bizplay_conversational.config.JsonNodeTypeHandler}
            )
            """)
    void insert(ConversationalAgentSession session);

    @Update("""
            UPDATE conversational_agent_session
            SET status = #{status},
                draft_json = #{draftJson, jdbcType=OTHER, typeHandler=com.api.bizplay_conversational.config.JsonNodeTypeHandler},
                chat_event_json = #{chatEventJson, jdbcType=OTHER, typeHandler=com.api.bizplay_conversational.config.JsonNodeTypeHandler},
                updated_date = NOW()
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    void update(ConversationalAgentSession session);

    default ConversationalAgentSession save(ConversationalAgentSession session) {
        boolean insert = session.getId() == null;
        session.applyDefaults();
        if (insert) {
            insert(session);
        } else {
            update(session);
        }
        return session;
    }
}
