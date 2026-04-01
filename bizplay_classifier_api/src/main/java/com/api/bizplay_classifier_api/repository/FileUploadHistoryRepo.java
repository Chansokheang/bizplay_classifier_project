package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import com.api.bizplay_classifier_api.model.request.FileUploadHistoryRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

@Mapper
public interface FileUploadHistoryRepo {

    @Select("""
        INSERT INTO file_upload_history (
            company_id,
            original_file_name,
            stored_file_name,
            file_url,
            sheet_name
        )
        VALUES (
            #{file.companyId},
            #{file.originalFileName},
            #{file.storedFileName},
            #{file.fileUrl},
            #{file.sheetName}
        )
        RETURNING *
    """)
    @Results(id = "fileUploadHistoryMap", value = {
            @Result(property = "fileId", column = "file_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "companyId", column = "company_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "originalFileName", column = "original_file_name"),
            @Result(property = "storedFileName", column = "stored_file_name"),
            @Result(property = "fileUrl", column = "file_url"),
            @Result(property = "sheetName", column = "sheet_name"),
            @Result(property = "createdDate", column = "created_date")
    })
    FileUploadHistoryDTO createFileRecord(@Param("file") FileUploadHistoryRequest fileUploadHistoryRequest);

    @Select("""
        SELECT * FROM file_upload_history WHERE file_id = #{fileId}
    """)
    @ResultMap("fileUploadHistoryMap")
    FileUploadHistoryDTO getFileById(@Param("fileId") java.util.UUID fileId);

    @Select("""
        SELECT *
        FROM file_upload_history
        WHERE company_id = #{companyId}
        ORDER BY created_date DESC
    """)
    @ResultMap("fileUploadHistoryMap")
    List<FileUploadHistoryDTO> getFilesByCompanyId(@Param("companyId") UUID companyId);

    @Select("""
        SELECT COUNT(1)
        FROM companies
        WHERE company_id = #{companyId}
          AND user_id = #{userId}
    """)
    int existsCompanyByIdAndUserId(@Param("companyId") UUID companyId, @Param("userId") UUID userId);
}
