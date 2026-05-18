package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.model.dto.CorpDTO;
import com.api.bizplay_classifier_api.model.dto.CorpGroupDTO;
import com.api.bizplay_classifier_api.model.request.CorpRequest;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CorpRepo {

    @Select("""
        SELECT
            c.corp_id,
            c.corp_group_id,
            c.corp_no,
            c.corp_name,
            cg.corp_group_cd,
            c.created_date
        FROM corp c
        JOIN corp_group cg ON cg.corp_group_id = c.corp_group_id
        ORDER BY c.created_date DESC
    """)
    @org.apache.ibatis.annotations.ResultMap("companyMap")
    List<CorpDTO> getAllCorps();

    @Select("""
        SELECT
            c.corp_id,
            c.corp_group_id,
            c.corp_no,
            c.corp_name,
            cg.corp_group_cd,
            c.created_date
        FROM corp c
        JOIN corp_group cg ON cg.corp_group_id = c.corp_group_id
        WHERE cg.corp_group_cd = #{corpGroupCode}
        ORDER BY c.created_date DESC
    """)
    @org.apache.ibatis.annotations.ResultMap("companyMap")
    List<CorpDTO> getAllCorpsByCorpGroupCode(@Param("corpGroupCode") String corpGroupCode);

    @Select("""
        SELECT COUNT(*) > 0
        FROM corp
        WHERE corp_no = #{corpNo}
    """)
    boolean existsBycorpNo(@Param("corpNo") String corpNo);

    @Select("""
        INSERT INTO corp_group (corp_group_cd)
        VALUES (#{corpGroupCode})
        RETURNING corp_group_id, corp_group_cd
    """)
    @Results(id = "corpGroupMap", value = {
            @Result(property = "corpGroupId", column = "corp_group_id"),
            @Result(property = "corpGroupCode", column = "corp_group_cd")
    })
    CorpGroupDTO createCorpGroup(@Param("corpGroupCode") String corpGroupCode);

    @Select("""
        SELECT *
        FROM corp_group
        ORDER BY corp_group_id ASC
    """)
    @org.apache.ibatis.annotations.ResultMap("corpGroupMap")
    List<CorpGroupDTO> getAllCorpGroups();

    @Select("""
        SELECT *
        FROM corp_group
        WHERE corp_group_cd = #{corpGroupCode}
    """)
    @org.apache.ibatis.annotations.ResultMap("corpGroupMap")
    CorpGroupDTO getCorpGroupByCode(@Param("corpGroupCode") String corpGroupCode);

    @Select("""
        SELECT COUNT(*) > 0
        FROM corp_group
        WHERE corp_group_cd = #{corpGroupCode}
    """)
    boolean existsCorpGroupByCode(@Param("corpGroupCode") String corpGroupCode);

    @Select("""
        INSERT INTO corp (corp_no, corp_group_id, corp_name)
        VALUES (
            #{corpNo},
            (SELECT corp_group_id FROM corp_group WHERE corp_group_cd = #{company.corpGroupCode}),
            #{company.corpName}
        )
        RETURNING
            corp_id,
            corp_group_id,
            corp_no,
            corp_name,
            created_date
    """)
    @org.apache.ibatis.annotations.ResultMap("companyMap")
    CorpDTO createCorp(
            @Param("company") CorpRequest corpRequest,
            @Param("corpNo") String corpNo
    );

    @Select("""
        SELECT
            c.corp_id,
            c.corp_group_id,
            c.corp_no,
            c.corp_name,
            cg.corp_group_cd,
            c.created_date
        FROM corp c
        JOIN corp_group cg ON cg.corp_group_id = c.corp_group_id
        WHERE c.corp_no = #{corpNo}
    """)
    @Results(id = "companyMap", value = {
            @Result(property = "corpId", column = "corp_id"),
            @Result(property = "corpGroupId", column = "corp_group_id"),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "corpName", column = "corp_name"),
            @Result(property = "corpGroupCode", column = "corp_group_cd"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "ruleDTOList", column = "corp_no", many = @Many(select = "com.api.bizplay_classifier_api.repository.RuleRepo.getAllRulesByCorpNo"))
    })
    CorpDTO getCorpByCorpNo(@Param("corpNo") String corpNo);

    @Delete("""
        DELETE FROM corp
        WHERE corp_no = #{corpNo}
    """)
    int deleteCorpByCorpNo(@Param("corpNo") String corpNo);
}
