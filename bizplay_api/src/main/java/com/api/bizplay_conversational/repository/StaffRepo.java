package com.api.bizplay_conversational.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_conversational.model.entity.Department;
import com.api.bizplay_conversational.model.entity.Staff;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface StaffRepo {

    @Select("""
            SELECT id, corp_no, name, created_date
            FROM conversational_department
            WHERE id = #{departmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    @Results(id = "staffDepartmentResultMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "name", column = "name"),
            @Result(property = "createdDate", column = "created_date")
    })
    Department findDepartmentById(@Param("departmentId") UUID departmentId);

    @Select("""
            SELECT s.id,
                   s.department_id,
                   s.name,
                   s.position,
                   s.created_date
            FROM conversational_staff s
            WHERE s.department_id = #{department.id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
              AND s.name = #{name}
              AND (
                    (#{position, jdbcType=VARCHAR} IS NULL AND s.position IS NULL)
                    OR s.position = #{position, jdbcType=VARCHAR}
              )
            LIMIT 1
            """)
    @Results(id = "staffResultMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "department", column = "department_id", one = @One(select = "findDepartmentById")),
            @Result(property = "name", column = "name"),
            @Result(property = "position", column = "position"),
            @Result(property = "createdDate", column = "created_date")
    })
    Staff findOneByNameAndDepartmentAndPosition(
            @Param("name") String name,
            @Param("department") Department department,
            @Param("position") String position
    );

    default Optional<Staff> findByNameAndDepartmentAndPosition(String name, Department department, String position) {
        return Optional.ofNullable(findOneByNameAndDepartmentAndPosition(name, department, position));
    }

    @Select("""
            SELECT s.id,
                   s.department_id,
                   s.name,
                   s.position,
                   s.created_date
            FROM conversational_staff s
            JOIN conversational_department d ON d.id = s.department_id
            WHERE d.corp_no = #{corpNo}
              AND LOWER(s.name) = LOWER(#{name})
            ORDER BY s.name ASC
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "department", column = "department_id", one = @One(select = "findDepartmentById")),
            @Result(property = "name", column = "name"),
            @Result(property = "position", column = "position"),
            @Result(property = "createdDate", column = "created_date")
    })
    List<Staff> findExactByCorpNoAndName(@Param("corpNo") String corpNo, @Param("name") String name);

    @Select("""
            SELECT s.id,
                   s.department_id,
                   s.name,
                   s.position,
                   s.created_date
            FROM conversational_staff s
            JOIN conversational_department d ON d.id = s.department_id
            WHERE d.corp_no = #{corpNo}
              AND s.name ILIKE CONCAT('%', #{name}, '%')
            ORDER BY s.name ASC
            """)
    @Results(value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "department", column = "department_id", one = @One(select = "findDepartmentById")),
            @Result(property = "name", column = "name"),
            @Result(property = "position", column = "position"),
            @Result(property = "createdDate", column = "created_date")
    })
    List<Staff> searchByCorpNoAndName(@Param("corpNo") String corpNo, @Param("name") String name);

    /** A single staff member by id (with department). Null if not found. */
    @Select("""
            SELECT s.id, s.department_id, s.name, s.position, s.created_date
            FROM conversational_staff s
            WHERE s.id = #{id}::uuid
            """)
    @ResultMap("staffResultMap")
    Staff findById(@Param("id") String id);

    /** All staff for a corp (newest first by name), each with department. */
    @Select("""
            SELECT s.id, s.department_id, s.name, s.position, s.created_date
            FROM conversational_staff s
            JOIN conversational_department d ON d.id = s.department_id
            WHERE d.corp_no = #{corpNo}
            ORDER BY s.name ASC
            """)
    @ResultMap("staffResultMap")
    List<Staff> findByCorpNo(@Param("corpNo") String corpNo);

    @Update("""
            UPDATE conversational_staff SET
                department_id = #{departmentId, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                name = #{name},
                position = #{position}
            WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}
            """)
    int update(@Param("id") UUID id, @Param("departmentId") UUID departmentId,
               @Param("name") String name, @Param("position") String position);

    @Delete("DELETE FROM conversational_staff WHERE id = #{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler}")
    int deleteById(@Param("id") UUID id);

    @Insert("""
            INSERT INTO conversational_staff (id, department_id, name, position)
            VALUES (#{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                    #{department.id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                    #{name},
                    #{position})
            """)
    void insert(Staff staff);

    default Staff save(Staff staff) {
        if (staff.getId() == null) {
            staff.setId(UUID.randomUUID());
        }
        insert(staff);
        return staff;
    }
}
