package com.api.bizplay_compliance.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_compliance.model.entity.ComplianceAudit;
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

/**
 * Persists the aggregate compliance_audit rows produced by the v2 R10 audit. The per-dimension
 * findings are stored as JSONB in rules_json via {@link com.api.bizplay_conversational.config.JsonNodeTypeHandler}.
 */
@Mapper
public interface ComplianceAuditRepo {

    @Insert("""
            INSERT INTO compliance_audit (
                id, corp_no, trip_plan_id, report_id, compliance_status, confidence_level, rules_json, created_date
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{corpNo},
                #{tripPlanId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{reportId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{complianceStatus},
                #{confidenceLevel},
                #{rulesJson, jdbcType=OTHER, typeHandler=com.api.bizplay_conversational.config.JsonNodeTypeHandler},
                #{createdDate}
            )
            """)
    void insertAudit(ComplianceAudit audit);

    @Select("""
            SELECT id, corp_no, trip_plan_id, report_id, compliance_status, confidence_level,
                   rules_json, created_date
            FROM compliance_audit
            WHERE trip_plan_id = #{tripPlanId}::uuid
            ORDER BY created_date DESC
            """)
    @Results(id = "complianceAuditMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "tripPlanId", column = "trip_plan_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "reportId", column = "report_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "complianceStatus", column = "compliance_status"),
            @Result(property = "confidenceLevel", column = "confidence_level"),
            @Result(property = "rulesJson", column = "rules_json", jdbcType = JdbcType.OTHER,
                    typeHandler = com.api.bizplay_conversational.config.JsonNodeTypeHandler.class),
            @Result(property = "createdDate", column = "created_date")
    })
    List<ComplianceAudit> findByTripPlanId(@Param("tripPlanId") String tripPlanId);

    @Select("""
            SELECT id, corp_no, trip_plan_id, report_id, compliance_status, confidence_level,
                   rules_json, created_date
            FROM compliance_audit
            WHERE corp_no = #{corpNo}
            ORDER BY created_date DESC
            """)
    @ResultMap("complianceAuditMap")
    List<ComplianceAudit> findByCorpNo(@Param("corpNo") String corpNo);

    /** A single audit by id, or null if not found. */
    @Select("""
            SELECT id, corp_no, trip_plan_id, report_id, compliance_status, confidence_level,
                   rules_json, created_date
            FROM compliance_audit
            WHERE id = #{id}::uuid
            """)
    @ResultMap("complianceAuditMap")
    ComplianceAudit findById(@Param("id") String id);

    /**
     * Override an audit's verdict. A null parameter leaves that column unchanged (partial update).
     * Returns the number of rows updated (0 if the id does not exist).
     */
    @Update("""
            UPDATE compliance_audit
               SET compliance_status = COALESCE(#{complianceStatus}, compliance_status),
                   confidence_level  = COALESCE(#{confidenceLevel}, confidence_level)
             WHERE id = #{id}::uuid
            """)
    int updateAuditStatus(@Param("id") String id,
                          @Param("complianceStatus") String complianceStatus,
                          @Param("confidenceLevel") String confidenceLevel);

    /** Delete one audit by id. Returns the number of rows deleted (0 if it did not exist). */
    @Delete("DELETE FROM compliance_audit WHERE id = #{id}::uuid")
    int deleteById(@Param("id") String id);
}
