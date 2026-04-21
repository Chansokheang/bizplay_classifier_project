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
import java.util.UUID;

@Mapper
public interface CorpRepo {

    @Select("""
        SELECT
            c.corp_id,
            c.corp_group_id,
            c.corp_no,
            c.user_id,
            c.corp_name,
            cg.corp_group_cd,
            c.created_date
        FROM corp c
        JOIN corp_group cg ON cg.corp_group_id = c.corp_group_id
        WHERE c.user_id = #{userId}
    """)
    @org.apache.ibatis.annotations.ResultMap("companyMap")
    List<CorpDTO> getAllCorpsByUserId(@Param("userId") UUID userId);

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
        WHERE corp_group_id = #{corpGroupId}
    """)
    @org.apache.ibatis.annotations.ResultMap("corpGroupMap")
    CorpGroupDTO getCorpGroupById(@Param("corpGroupId") Long corpGroupId);

    @Select("""
        SELECT COUNT(*) > 0
        FROM corp_group
        WHERE corp_group_id = #{corpGroupId}
    """)
    boolean existsCorpGroupById(@Param("corpGroupId") Long corpGroupId);

    @Select("""
        SELECT COUNT(*) > 0
        FROM corp_group
        WHERE corp_group_cd = #{corpGroupCode}
    """)
    boolean existsCorpGroupByCode(@Param("corpGroupCode") String corpGroupCode);

    @Select("""
        INSERT INTO corp (corp_no, user_id, corp_group_id, corp_name)
        VALUES (#{corpNo}, #{userId}, #{company.corpGroupId}, #{company.corpName})
        RETURNING
            corp_id,
            corp_group_id,
            corp_no,
            user_id,
            corp_name,
            created_date
    """)
    @org.apache.ibatis.annotations.ResultMap("companyMap")
    CorpDTO createCorp(
            @Param("company") CorpRequest corpRequest,
            @Param("userId") UUID userId,
            @Param("corpNo") String corpNo
    );

    @Select("""
        SELECT
            c.corp_id,
            c.corp_group_id,
            c.corp_no,
            c.user_id,
            c.corp_name,
            cg.corp_group_cd,
            c.created_date
        FROM corp c
        JOIN corp_group cg ON cg.corp_group_id = c.corp_group_id
        WHERE c.corp_no = #{corpNo}
          AND c.user_id = #{userId}
    """)
    @Results(id = "companyMap", value = {
            @Result(property = "corpId", column = "corp_id"),
            @Result(property = "corpGroupId", column = "corp_group_id"),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "corpName", column = "corp_name"),
            @Result(property = "corpGroupCode", column = "corp_group_cd"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "ruleDTOList", column = "corp_no", many = @Many(select = "com.api.bizplay_classifier_api.repository.RuleRepo.getAllRulesByCorpNo"))
    })
    CorpDTO getCorpByCorpNo(@Param("userId") UUID userId, @Param("corpNo") String corpNo);

    @Delete("""
        DELETE FROM corp
        WHERE corp_no = #{corpNo}
          AND user_id = #{userId}
    """)
    int deleteCorpByCorpNo(@Param("userId") UUID userId, @Param("corpNo") String corpNo);
}

