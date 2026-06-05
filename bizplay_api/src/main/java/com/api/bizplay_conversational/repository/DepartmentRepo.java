package com.api.bizplay_conversational.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_conversational.model.entity.Department;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.type.JdbcType;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface DepartmentRepo {

    @Select("""
            SELECT id, corp_no, name, created_date
            FROM conversational_department
            WHERE corp_no = #{corpNo}
              AND name = #{name}
            LIMIT 1
            """)
    @Results(id = "departmentResultMap", value = {
            @Result(property = "id", column = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "name", column = "name"),
            @Result(property = "createdDate", column = "created_date")
    })
    Department findOneByCorpNoAndName(@Param("corpNo") String corpNo, @Param("name") String name);

    default Optional<Department> findByCorpNoAndName(String corpNo, String name) {
        return Optional.ofNullable(findOneByCorpNoAndName(corpNo, name));
    }

    @Insert("""
            INSERT INTO conversational_department (id, corp_no, name)
            VALUES (#{id, jdbcType=OTHER, typeHandler=com.api.bizplay_classifier_api.config.UUIDTypeHandler},
                    #{corpNo},
                    #{name})
            """)
    void insert(Department department);

    default Department save(Department department) {
        if (department.getId() == null) {
            department.setId(UUID.randomUUID());
        }
        insert(department);
        return department;
    }
}
