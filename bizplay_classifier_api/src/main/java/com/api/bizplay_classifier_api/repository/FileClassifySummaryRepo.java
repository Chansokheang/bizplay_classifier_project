package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.FileClassifySummaryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

@Mapper
public interface FileClassifySummaryRepo {

    @Select("""
        INSERT INTO file_classify_summary (
            file_id,
            company_id,
            total_rows,
            processed_rows,
            skipped_rows,
            rule_matched_rows,
            ai_matched_rows,
            unmatched_rows,
            updated_date
        )
        VALUES (
            #{fileId},
            #{companyId},
            #{totalRows},
            #{processedRows},
            #{skippedRows},
            #{ruleMatchedRows},
            #{aiMatchedRows},
            #{unmatchedRows},
            NOW()
        )
        RETURNING *
    """)
    @Results(id = "fileClassifySummaryMap", value = {
            @Result(property = "summaryId", column = "summary_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "fileId", column = "file_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyId", column = "company_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "totalRows", column = "total_rows"),
            @Result(property = "processedRows", column = "processed_rows"),
            @Result(property = "skippedRows", column = "skipped_rows"),
            @Result(property = "ruleMatchedRows", column = "rule_matched_rows"),
            @Result(property = "aiMatchedRows", column = "ai_matched_rows"),
            @Result(property = "unmatchedRows", column = "unmatched_rows"),
            @Result(property = "createdDate", column = "created_date"),
            @Result(property = "updatedDate", column = "updated_date")
    })
    FileClassifySummaryDTO createSummary(
            @Param("fileId") UUID fileId,
            @Param("companyId") UUID companyId,
            @Param("totalRows") int totalRows,
            @Param("processedRows") int processedRows,
            @Param("skippedRows") int skippedRows,
            @Param("ruleMatchedRows") int ruleMatchedRows,
            @Param("aiMatchedRows") int aiMatchedRows,
            @Param("unmatchedRows") int unmatchedRows
    );

    @Select("""
        SELECT *
        FROM file_classify_summary
        WHERE company_id = #{companyId}
        ORDER BY created_date DESC
    """)
    @org.apache.ibatis.annotations.ResultMap("fileClassifySummaryMap")
    List<FileClassifySummaryDTO> getAllByCompanyId(@Param("companyId") UUID companyId);

    @Select("""
        SELECT *
        FROM file_classify_summary
        WHERE file_id = #{fileId}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    @org.apache.ibatis.annotations.ResultMap("fileClassifySummaryMap")
    FileClassifySummaryDTO getLatestByFileId(@Param("fileId") UUID fileId);
}
