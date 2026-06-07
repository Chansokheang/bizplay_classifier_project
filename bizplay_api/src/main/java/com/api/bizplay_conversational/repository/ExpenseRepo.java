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
                start_date, end_date, description, proof_date, amount_used, regulated_amount
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{expenseType}, #{taxCode}, #{category}, #{usePurpose}, #{account},
                #{startDate}, #{endDate}, #{description}, #{proofDate}, #{amountUsed}, #{regulatedAmount}
            )
            """)
    void insertCostExpense(CostExpense expense);

    @Insert("""
            INSERT INTO conversational_transportation_expense (
                id, tax_code, category, use_purpose, account, transportation_method,
                origin_location, destination_location, usage_date, vendor,
                supply_price, tax, amount_used, regulated_amount
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{taxCode}, #{category}, #{usePurpose}, #{account}, #{transportationMethod},
                #{originLocation}, #{destinationLocation}, #{usageDate}, #{vendor},
                #{supplyPrice}, #{tax}, #{amountUsed}, #{regulatedAmount}
            )
            """)
    void insertTransportationExpense(TransportationExpense expense);

    @Insert("""
            INSERT INTO conversational_trip_report (
                id, agent_session_id, department_id, trip_plan_id,
                transportation_expense_id, cost_expense_id, section_code,
                excess_reason, briefs, note, approval_number
            ) VALUES (
                #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{agentSessionId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{departmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{tripPlanId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{transportationExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{costExpenseId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                #{sectionCode}, #{excessReason}, #{briefs}, #{note}, #{approvalNumber}
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
                   excess_reason, briefs, note, approval_number
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
            @Result(property = "excessReason", column = "excess_reason"),
            @Result(property = "briefs", column = "briefs"),
            @Result(property = "note", column = "note"),
            @Result(property = "approvalNumber", column = "approval_number")
    })
    TripReport findTripReportById(@Param("id") String id);

    /** All report lines for a corp (via their trip plan), newest first. */
    @Select("""
            SELECT r.id, r.agent_session_id, r.department_id, r.trip_plan_id,
                   r.transportation_expense_id, r.cost_expense_id, r.section_code,
                   r.excess_reason, r.briefs, r.note, r.approval_number
            FROM conversational_trip_report r
            JOIN conversational_trip_plan p ON p.id = r.trip_plan_id
            WHERE p.corp_no = #{corpNo}
            ORDER BY r.created_date DESC
            """)
    @ResultMap("tripReportMap")
    List<TripReport> findTripReportsByCorpNo(@Param("corpNo") String corpNo);

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
                   start_date, end_date, description, proof_date, amount_used, regulated_amount
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
            @Result(property = "description", column = "description"),
            @Result(property = "proofDate", column = "proof_date"),
            @Result(property = "amountUsed", column = "amount_used"),
            @Result(property = "regulatedAmount", column = "regulated_amount")
    })
    CostExpense findCostExpenseById(@Param("id") UUID id);

    /** A transportation expense row by id. Null if not found. */
    @Select("""
            SELECT id, tax_code, category, use_purpose, account, transportation_method,
                   origin_location, destination_location, usage_date, vendor,
                   supply_price, tax, amount_used, regulated_amount
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
            @Result(property = "originLocation", column = "origin_location"),
            @Result(property = "destinationLocation", column = "destination_location"),
            @Result(property = "usageDate", column = "usage_date"),
            @Result(property = "vendor", column = "vendor"),
            @Result(property = "supplyPrice", column = "supply_price"),
            @Result(property = "tax", column = "tax"),
            @Result(property = "amountUsed", column = "amount_used"),
            @Result(property = "regulatedAmount", column = "regulated_amount")
    })
    TransportationExpense findTransportationExpenseById(@Param("id") UUID id);

    /** The department name for an id, or null. */
    @Select("""
            SELECT name FROM conversational_department
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    String findDepartmentNameById(@Param("id") UUID id);
}
