package com.api.bizplay_conversational.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_conversational.model.entity.Plan;
import com.api.bizplay_conversational.model.entity.PlanAttachment;
import com.api.bizplay_conversational.model.entity.PlanTraveler;
import com.api.bizplay_conversational.model.response.PlanAttachmentResponse;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.PlanTravelerResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

@Mapper
public interface PlanRepo {

    /** All plans for a corp (newest first), without travelers/attachments (loaded separately). */
    @Select("""
            SELECT id, corp_no, agent_session_id, plan_type, purpose, title, content,
                   business_period, business_start_date, business_end_date, destination,
                   business_trip_classification, approval_status, created_date
            FROM conversational_trip_plan
            WHERE corp_no = #{corpNo}
            ORDER BY created_date DESC
            """)
    @Results(id = "planResponseMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "agentSessionId", column = "agent_session_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "planType", column = "plan_type"),
            @Result(property = "purpose", column = "purpose"),
            @Result(property = "title", column = "title"),
            @Result(property = "content", column = "content"),
            @Result(property = "businessPeriod", column = "business_period"),
            @Result(property = "businessStartDate", column = "business_start_date"),
            @Result(property = "businessEndDate", column = "business_end_date"),
            @Result(property = "destination", column = "destination"),
            @Result(property = "businessTripClassification", column = "business_trip_classification"),
            @Result(property = "approvalStatus", column = "approval_status"),
            @Result(property = "createdAt", column = "created_date")
    })
    List<PlanResponse> findPlanResponsesByCorpNo(@Param("corpNo") String corpNo);

    /** A single plan by id, without travelers/attachments (loaded separately). Null if not found. */
    @Select("""
            SELECT id, corp_no, agent_session_id, plan_type, purpose, title, content,
                   business_period, business_start_date, business_end_date, destination,
                   business_trip_classification, approval_status, created_date
            FROM conversational_trip_plan
            WHERE id = #{id}::uuid
            """)
    @ResultMap("planResponseMap")
    PlanResponse findPlanResponseById(@Param("id") String id);

    /** Plans for a corp filtered by approval_status (newest first), without travelers/attachments. */
    @Select("""
            SELECT id, corp_no, agent_session_id, plan_type, purpose, title, content,
                   business_period, business_start_date, business_end_date, destination,
                   business_trip_classification, approval_status, created_date
            FROM conversational_trip_plan
            WHERE corp_no = #{corpNo}
              AND approval_status = #{approvalStatus}
            ORDER BY created_date DESC
            """)
    @ResultMap("planResponseMap")
    List<PlanResponse> findPlanResponsesByCorpNoAndApprovalStatus(@Param("corpNo") String corpNo,
                                                                  @Param("approvalStatus") String approvalStatus);

    /** The approval_status of a plan, or null if the plan does not exist. */
    @Select("""
            SELECT approval_status
            FROM conversational_trip_plan
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    String findApprovalStatusById(@Param("id") java.util.UUID id);

    /** Update only the approval_status of a plan. Returns the number of rows updated (0 if not found). */
    @Update("""
            UPDATE conversational_trip_plan
               SET approval_status = #{approvalStatus},
                   updated_date = NOW()
             WHERE id = #{planId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    int updateApprovalStatus(@Param("planId") java.util.UUID planId,
                             @Param("approvalStatus") String approvalStatus);

    /** Travelers of a plan (joined to staff + department) as response rows. */
    @Select("""
            SELECT t.staff_id            AS id,
                   s.name                AS name,
                   d.name                AS department,
                   s.position            AS position,
                   t.origin_location     AS origin,
                   t.destination_location AS destination,
                   t.return_point        AS return_point
            FROM conversational_traveler t
            JOIN conversational_staff s ON s.id = t.staff_id
            LEFT JOIN conversational_department d ON d.id = s.department_id
            WHERE t.trip_id = #{planId}::uuid
            ORDER BY s.name
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "name", column = "name"),
            @Result(property = "department", column = "department"),
            @Result(property = "position", column = "position"),
            @Result(property = "origin", column = "origin"),
            @Result(property = "destination", column = "destination"),
            @Result(property = "returnPoint", column = "return_point")
    })
    List<PlanTravelerResponse> findTravelerResponsesByPlanId(@Param("planId") String planId);

    /** Attachments of a plan as response rows. */
    @Select("""
            SELECT id, attachment_type AS type, file_id, url
            FROM conversational_attachment
            WHERE trip_plan_id = #{planId}::uuid
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "type", column = "type"),
            @Result(property = "fileId", column = "file_id"),
            @Result(property = "url", column = "url")
    })
    List<PlanAttachmentResponse> findAttachmentResponsesByPlanId(@Param("planId") String planId);

    /** Plan ids linked to a conversational session (newest first). */
    @Select("""
            SELECT id::text
            FROM conversational_trip_plan
            WHERE agent_session_id = #{agentSessionId}::uuid
            ORDER BY created_date DESC
            """)
    List<String> findPlanIdsByAgentSessionId(@Param("agentSessionId") String agentSessionId);

    /** Update the mutable columns of a plan (id / user_req_id / agent_session_id / created_date kept). */
    @Update("""
            UPDATE conversational_trip_plan SET
                corp_no = #{corpNo},
                plan_type = #{planType},
                purpose = #{purpose},
                title = #{title},
                content = #{content},
                business_period = #{businessPeriod},
                business_start_date = #{businessStartDate},
                business_end_date = #{businessEndDate},
                destination = #{destination},
                business_trip_classification = #{businessTripClassification},
                extras = #{extras, jdbcType=OTHER, typeHandler=com.api.bizplay_conversational.config.JsonNodeTypeHandler}
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    void updatePlan(Plan plan);

    @Delete("DELETE FROM conversational_traveler WHERE trip_id = #{planId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteTravelersByPlanId(@Param("planId") java.util.UUID planId);

    @Delete("DELETE FROM conversational_attachment WHERE trip_plan_id = #{planId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteAttachmentsByPlanId(@Param("planId") java.util.UUID planId);

    /** Delete the plan row itself. Returns the number of rows deleted (0 if it did not exist). */
    @Delete("DELETE FROM conversational_trip_plan WHERE id = #{planId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    int deletePlanById(@Param("planId") java.util.UUID planId);

    /** The agent session this plan mirrors (null for manually created plans). */
    @org.apache.ibatis.annotations.Select("SELECT agent_session_id FROM conversational_trip_plan WHERE id = #{planId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    String findAgentSessionIdByPlanId(@Param("planId") java.util.UUID planId);

    @Insert("""
            INSERT INTO conversational_trip_plan (
                id,
                corp_no,
                user_req_id,
                agent_session_id,
                plan_type,
                purpose,
                title,
                content,
                business_period,
                business_start_date,
                business_end_date,
                destination,
                business_trip_classification,
                extras
            )
            VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{corpNo},
                #{userReqId},
                #{agentSessionId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{planType},
                #{purpose},
                #{title},
                #{content},
                #{businessPeriod},
                #{businessStartDate},
                #{businessEndDate},
                #{destination},
                #{businessTripClassification},
                #{extras, jdbcType=OTHER, typeHandler=com.api.bizplay_conversational.config.JsonNodeTypeHandler}
            )
            """)
    void insertPlan(Plan plan);

    @Insert("""
            INSERT INTO conversational_traveler (
                trip_id,
                staff_id,
                origin_location,
                destination_location,
                return_point
            )
            VALUES (
                #{plan.id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{staff.id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{origin},
                #{destination},
                #{returnPoint}
            )
            ON CONFLICT (trip_id, staff_id) DO UPDATE
            SET origin_location = EXCLUDED.origin_location,
                destination_location = EXCLUDED.destination_location,
                return_point = EXCLUDED.return_point
            """)
    void insertTraveler(PlanTraveler traveler);

    @Insert("""
            INSERT INTO conversational_attachment (
                id,
                trip_plan_id,
                file_id,
                attachment_type,
                url
            )
            VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{plan.id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{fileId},
                'PLAN',
                #{url}
            )
            """)
    void insertAttachment(PlanAttachment attachment);
}
