package com.api.bizplay_conversational.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_conversational.model.entity.CostExpense;
import com.api.bizplay_conversational.model.entity.TransportationExpense;
import com.api.bizplay_conversational.model.entity.TripReport;
import com.api.bizplay_conversational.model.entity.TripReportDetail;
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
import java.util.UUID;

/**
 * Persists an approved expense report as a HEADER (conversational_trip_report) with many DETAIL
 * lines (conversational_trip_report_detail), each linking to a cost/transportation expense and,
 * optionally, the source receipt attachment. The conversational draft_json is normalized into these
 * at POST time.
 */
@Mapper
public interface ExpenseRepo {

    // --- header ------------------------------------------------------------------

    @Insert("""
            INSERT INTO conversational_trip_report (
                id, agent_session_id, department_id, trip_plan_id, approval_number, approval_status
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{agentSessionId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{departmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{tripPlanId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{approvalNumber}, #{approvalStatus}
            )
            """)
    void insertTripReport(TripReport report);

    @Select("""
            SELECT id, agent_session_id, department_id, trip_plan_id,
                   approval_number, approval_status, created_date
            FROM conversational_trip_report
            WHERE id = #{id}::uuid
            """)
    @Results(id = "tripReportMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "agentSessionId", column = "agent_session_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "departmentId", column = "department_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "tripPlanId", column = "trip_plan_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "approvalStatus", column = "approval_status"),
            @Result(property = "createdDate", column = "created_date")
    })
    TripReport findTripReportById(@Param("id") String id);

    /** All report headers for a corp (via their trip plan), newest first. */
    @Select("""
            SELECT r.id, r.agent_session_id, r.department_id, r.trip_plan_id,
                   r.approval_number, r.approval_status, r.created_date
            FROM conversational_trip_report r
            JOIN conversational_trip_plan p ON p.id = r.trip_plan_id
            WHERE p.corp_no = #{corpNo}
            ORDER BY r.created_date DESC
            """)
    @ResultMap("tripReportMap")
    List<TripReport> findTripReportsByCorpNo(@Param("corpNo") String corpNo);

    /** All report headers for a single trip plan, newest first (used by the compliance R10 audit). */
    @Select("""
            SELECT id, agent_session_id, department_id, trip_plan_id,
                   approval_number, approval_status, created_date
            FROM conversational_trip_report
            WHERE trip_plan_id = #{tripPlanId}::uuid
            ORDER BY created_date DESC
            """)
    @ResultMap("tripReportMap")
    List<TripReport> findTripReportsByTripPlanId(@Param("tripPlanId") String tripPlanId);

    /** Delete a report header. Returns rows deleted. Details + attachments cascade via FK. */
    @Delete("DELETE FROM conversational_trip_report WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    int deleteTripReportById(@Param("id") UUID id);

    @Update("""
            UPDATE conversational_trip_report
               SET approval_status = #{approvalStatus}
             WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    int updateApprovalStatus(@Param("id") UUID id, @Param("approvalStatus") String approvalStatus);

    @Update("""
            UPDATE conversational_trip_report
               SET department_id = #{departmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                   approval_number = #{approvalNumber},
                   approval_status = #{approvalStatus}
             WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    int updateTripReportHeader(TripReport report);

    // --- detail ------------------------------------------------------------------

    @Insert("""
            INSERT INTO conversational_trip_report_detail (
                id, trip_report_id, section_code,
                transportation_expense_id, cost_expense_id, conversational_attachment_id
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{tripReportId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{sectionCode},
                #{transportationExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{costExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{conversationalAttachmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            )
            """)
    void insertTripReportDetail(TripReportDetail detail);

    @Select("""
            SELECT id, trip_report_id, section_code,
                   transportation_expense_id, cost_expense_id, conversational_attachment_id
            FROM conversational_trip_report_detail
            WHERE trip_report_id = #{reportId}::uuid
            ORDER BY created_date
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "tripReportId", column = "trip_report_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "sectionCode", column = "section_code"),
            @Result(property = "transportationExpenseId", column = "transportation_expense_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "costExpenseId", column = "cost_expense_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "conversationalAttachmentId", column = "conversational_attachment_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class)
    })
    List<TripReportDetail> findDetailsByReportId(@Param("reportId") String reportId);

    @Delete("DELETE FROM conversational_trip_report_detail WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteTripReportDetailById(@Param("id") UUID id);

    // --- expenses ----------------------------------------------------------------

    @Insert("""
            INSERT INTO conversational_cost_expense (
                id, expense_type, tax_code, category, use_purpose, account,
                start_date, end_date, evidence_date, description,
                policy_amount, application_amount, excess_reason, note
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{expenseType}, #{taxCode}, #{category}, #{usePurpose}, #{account},
                #{startDate}, #{endDate}, #{evidenceDate}, #{description},
                #{policyAmount}, #{applicationAmount}, #{excessReason}, #{note}
            )
            """)
    void insertCostExpense(CostExpense expense);

    @Insert("""
            INSERT INTO conversational_transportation_expense (
                id, tax_code, category, use_purpose, account, transportation_method, grade,
                origin_location, destination_location, usage_date, evidence_date, vendor,
                supply_price, tax, policy_amount, application_amount, excess_reason, description, note
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{taxCode}, #{category}, #{usePurpose}, #{account}, #{transportationMethod}, #{grade},
                #{originLocation}, #{destinationLocation}, #{usageDate}, #{evidenceDate}, #{vendor},
                #{supplyPrice}, #{tax}, #{policyAmount}, #{applicationAmount}, #{excessReason}, #{description}, #{note}
            )
            """)
    void insertTransportationExpense(TransportationExpense expense);

    @Select("""
            SELECT id, expense_type, tax_code, category, use_purpose, account,
                   start_date, end_date, evidence_date, description,
                   policy_amount, application_amount, excess_reason, note
            FROM conversational_cost_expense
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "expenseType", column = "expense_type"),
            @Result(property = "taxCode", column = "tax_code"),
            @Result(property = "category", column = "category"),
            @Result(property = "usePurpose", column = "use_purpose"),
            @Result(property = "account", column = "account"),
            @Result(property = "startDate", column = "start_date"),
            @Result(property = "endDate", column = "end_date"),
            @Result(property = "evidenceDate", column = "evidence_date"),
            @Result(property = "description", column = "description"),
            @Result(property = "policyAmount", column = "policy_amount"),
            @Result(property = "applicationAmount", column = "application_amount"),
            @Result(property = "excessReason", column = "excess_reason"),
            @Result(property = "note", column = "note")
    })
    CostExpense findCostExpenseById(@Param("id") UUID id);

    @Select("""
            SELECT id, tax_code, category, use_purpose, account, transportation_method, grade,
                   origin_location, destination_location, usage_date, evidence_date, vendor,
                   supply_price, tax, policy_amount, application_amount, excess_reason, description, note
            FROM conversational_transportation_expense
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "taxCode", column = "tax_code"),
            @Result(property = "category", column = "category"),
            @Result(property = "usePurpose", column = "use_purpose"),
            @Result(property = "account", column = "account"),
            @Result(property = "transportationMethod", column = "transportation_method"),
            @Result(property = "grade", column = "grade"),
            @Result(property = "originLocation", column = "origin_location"),
            @Result(property = "destinationLocation", column = "destination_location"),
            @Result(property = "usageDate", column = "usage_date"),
            @Result(property = "evidenceDate", column = "evidence_date"),
            @Result(property = "vendor", column = "vendor"),
            @Result(property = "supplyPrice", column = "supply_price"),
            @Result(property = "tax", column = "tax"),
            @Result(property = "policyAmount", column = "policy_amount"),
            @Result(property = "applicationAmount", column = "application_amount"),
            @Result(property = "excessReason", column = "excess_reason"),
            @Result(property = "description", column = "description"),
            @Result(property = "note", column = "note")
    })
    TransportationExpense findTransportationExpenseById(@Param("id") UUID id);

    @Delete("DELETE FROM conversational_cost_expense WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteCostExpenseById(@Param("id") UUID id);

    @Delete("DELETE FROM conversational_transportation_expense WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteTransportationExpenseById(@Param("id") UUID id);

    // --- attachments + department ------------------------------------------------

    @Insert("""
            INSERT INTO conversational_attachment (id, report_id, file_id, attachment_type, url)
            VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{reportId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{fileId}, 'REPORT', #{url}
            )
            """)
    void insertReportAttachment(@Param("id") UUID id,
                                @Param("reportId") UUID reportId,
                                @Param("fileId") String fileId,
                                @Param("url") String url);

    /** The file_id of an attachment by id, or null. */
    @Select("""
            SELECT file_id FROM conversational_attachment
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    String findAttachmentFileIdById(@Param("id") UUID id);

    /** Delete all attachments owned by a report header (used when replacing a report's content). */
    @Delete("DELETE FROM conversational_attachment WHERE report_id = #{reportId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteReportAttachmentsByReportId(@Param("reportId") UUID reportId);

    /** The department name for an id, or null. */
    @Select("""
            SELECT name FROM conversational_department
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    String findDepartmentNameById(@Param("id") UUID id);
}
