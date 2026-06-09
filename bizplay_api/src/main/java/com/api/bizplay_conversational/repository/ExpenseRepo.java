package com.api.bizplay_conversational.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_conversational.model.entity.CostExpense;
import com.api.bizplay_conversational.model.entity.TransportationExpense;
import com.api.bizplay_conversational.model.entity.TripReport;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

/**
 * Persists an approved expense report: the normalized rows of conversational_cost_expense,
 * conversational_transportation_expense, conversational_trip_report (one per expense line), and
 * REPORT attachments. The conversational drafting (draft_json) is normalized into these at POST time.
 */
@Mapper
public interface ExpenseRepo {

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

    @Insert("""
            INSERT INTO conversational_trip_report (
                id, agent_session_id, department_id, trip_plan_id,
                transportation_expense_id, cost_expense_id, section_code, approval_number
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{agentSessionId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{departmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{tripPlanId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{transportationExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{costExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{sectionCode}, #{approvalNumber}
            )
            """)
    void insertTripReport(TripReport report);

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

    // --- reads -------------------------------------------------------------------

    /** A single report line (conversational_trip_report) by id. Null if not found. */
    @Select("""
            SELECT id, agent_session_id, department_id, trip_plan_id,
                   transportation_expense_id, cost_expense_id, section_code,
                   approval_number, approval_status
            FROM conversational_trip_report
            WHERE id = #{id}::uuid
            """)
    @Results(id = "tripReportMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "agentSessionId", column = "agent_session_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "departmentId", column = "department_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "tripPlanId", column = "trip_plan_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "transportationExpenseId", column = "transportation_expense_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "costExpenseId", column = "cost_expense_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "sectionCode", column = "section_code"),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "approvalStatus", column = "approval_status")
    })
    TripReport findTripReportById(@Param("id") String id);

    /** All report lines for a corp (via their trip plan), newest first. */
    @Select("""
            SELECT r.id, r.agent_session_id, r.department_id, r.trip_plan_id,
                   r.transportation_expense_id, r.cost_expense_id, r.section_code,
                   r.approval_number, r.approval_status
            FROM conversational_trip_report r
            JOIN conversational_trip_plan p ON p.id = r.trip_plan_id
            WHERE p.corp_no = #{corpNo}
            ORDER BY r.created_date DESC
            """)
    @ResultMap("tripReportMap")
    List<TripReport> findTripReportsByCorpNo(@Param("corpNo") String corpNo);

    /** Full update of a report line's mutable columns (section, expense FKs, department, approvals). */
    @org.apache.ibatis.annotations.Update("""
            UPDATE conversational_trip_report SET
                section_code = #{sectionCode},
                transportation_expense_id = #{transportationExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                cost_expense_id = #{costExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                department_id = #{departmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                approval_number = #{approvalNumber},
                approval_status = #{approvalStatus}
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    int updateTripReportLine(TripReport report);

    /** Update only the approval_status of a report line. Returns rows updated (0 if not found). */
    @org.apache.ibatis.annotations.Update("""
            UPDATE conversational_trip_report
               SET approval_status = #{approvalStatus}
             WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    int updateApprovalStatus(@Param("id") UUID id, @Param("approvalStatus") String approvalStatus);

    /** Delete a report line. Returns rows deleted (0 if not found). Attachments cascade via report_id. */
    @Delete("DELETE FROM conversational_trip_report WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    int deleteTripReportById(@Param("id") UUID id);

    @Delete("DELETE FROM conversational_cost_expense WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteCostExpenseById(@Param("id") UUID id);

    @Delete("DELETE FROM conversational_transportation_expense WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    void deleteTransportationExpenseById(@Param("id") UUID id);

    /** A cost/etc expense row by id. Null if not found. */
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

    /** A transportation expense row by id. Null if not found. */
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

    /** The department name for an id, or null. */
    @Select("""
            SELECT name FROM conversational_department
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    String findDepartmentNameById(@Param("id") UUID id);
}
