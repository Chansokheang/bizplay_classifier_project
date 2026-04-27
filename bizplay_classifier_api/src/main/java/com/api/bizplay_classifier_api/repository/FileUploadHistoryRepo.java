package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.FileUploadHistoryDTO;
import com.api.bizplay_classifier_api.model.request.FileUploadHistoryRequest;
import org.apache.ibatis.annotations.Delete;
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
        INSERT INTO classifier_file_upload_history (
            corp_no,
            original_file_name,
            stored_file_name,
            file_url,
            sheet_name,
            file_type
        )
        VALUES (
            #{file.corpNo},
            #{file.originalFileName},
            #{file.storedFileName},
            #{file.fileUrl},
            #{file.sheetName},
            #{file.fileType}
        )
        RETURNING *
    """)
    @Results(id = "fileUploadHistoryMap", value = {
            @Result(property = "fileId", column = "file_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "corpNo", column = "corp_no"),
            @Result(property = "originalFileName", column = "original_file_name"),
            @Result(property = "storedFileName", column = "stored_file_name"),
            @Result(property = "fileUrl", column = "file_url"),
            @Result(property = "sheetName", column = "sheet_name"),
            @Result(property = "fileType", column = "file_type", javaType = com.api.bizplay_classifier_api.model.enums.FileType.class),
            @Result(property = "createdDate", column = "created_date")
    })
    FileUploadHistoryDTO createFileRecord(@Param("file") FileUploadHistoryRequest fileUploadHistoryRequest);

    @Select("""
        SELECT * FROM classifier_file_upload_history WHERE file_id = #{fileId}
    """)
    @ResultMap("fileUploadHistoryMap")
    FileUploadHistoryDTO getFileById(@Param("fileId") UUID fileId);

    @Select("""
        SELECT *
        FROM classifier_file_upload_history
        WHERE corp_no = #{corpNo}
        ORDER BY created_date DESC
    """)
    @ResultMap("fileUploadHistoryMap")
    List<FileUploadHistoryDTO> getFilesByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        SELECT *
        FROM classifier_file_upload_history
        WHERE corp_no = #{corpNo}
          AND file_type = #{fileType}
        ORDER BY created_date ASC
    """)
    @ResultMap("fileUploadHistoryMap")
    List<FileUploadHistoryDTO> getFilesByCorpNoAndFileType(
            @Param("corpNo") String corpNo,
            @Param("fileType") com.api.bizplay_classifier_api.model.enums.FileType fileType
    );

    @Select("""
        SELECT *
        FROM classifier_file_upload_history
        WHERE corp_no = #{corpNo}
        ORDER BY created_date DESC
        LIMIT 1
    """)
    @ResultMap("fileUploadHistoryMap")
    FileUploadHistoryDTO getLatestFileByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        SELECT *
        FROM classifier_file_upload_history
        WHERE corp_no = #{corpNo}
          AND file_type = 'TRAINING'
        ORDER BY created_date DESC
        LIMIT 1
    """)
    @ResultMap("fileUploadHistoryMap")
    FileUploadHistoryDTO getLatestTrainingFileByCorpNo(@Param("corpNo") String corpNo);

    @Select("""
        SELECT COUNT(1)
        FROM corp
        WHERE corp_no = #{corpNo}
    """)
    int existsCorpByCorpNo(@Param("corpNo") String corpNo);

    @Delete("""
        DELETE FROM classifier_file_upload_history
        WHERE file_id = #{fileId}
    """)
    int deleteFileById(@Param("fileId") UUID fileId);

    default List<FileUploadHistoryDTO> getFilesByCompanyId(String companyId) {
        return getFilesByCorpNo(companyId);
    }

    default List<FileUploadHistoryDTO> getFilesByCompanyIdAndFileType(String companyId,
                                                                      com.api.bizplay_classifier_api.model.enums.FileType fileType) {
        return getFilesByCorpNoAndFileType(companyId, fileType);
    }

    default FileUploadHistoryDTO getLatestFileByCompanyId(String companyId) {
        return getLatestFileByCorpNo(companyId);
    }

    default FileUploadHistoryDTO getLatestTrainingFileByCompanyId(String companyId) {
        return getLatestTrainingFileByCorpNo(companyId);
    }

    default int existsCompanyById(String companyId) {
        return existsCorpByCorpNo(companyId);
    }
}

