package com.api.bizplay_conversational.repository;

import com.api.bizplay_conversational.model.entity.Plan;
import com.api.bizplay_conversational.model.entity.PlanAttachment;
import com.api.bizplay_conversational.model.entity.PlanTraveler;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PlanRepo {

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
