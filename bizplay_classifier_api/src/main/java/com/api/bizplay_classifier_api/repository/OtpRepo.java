package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.OtpDTO;
import com.api.bizplay_classifier_api.model.entity.Otps;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

@Mapper
public interface OtpRepo {

    @Select("""
        INSERT INTO otps (user_id, otp_code, issued_date, expiration) VALUES (#{otp.userId}, #{otp.otpCode}, #{otp.issuedDate}, #{otp.expiration}) RETURNING *
    """)
    @Results(id = "otpMap", value = {
            @Result(property = "otpId", column = "otp_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "userId", column = "user_id", jdbcType = JdbcType.OTHER, typeHandler = UUIDTypeHandler.class),
            @Result(property = "otpCode", column = "otp_code"),
            @Result(property = "issuedDate", column = "issued_date"),
            @Result(property = "expiration", column = "expiration"),
            @Result(property = "isVerified", column = "is_verified")
    })
    Otps insertOtp(@Param("otp") OtpDTO otpDTO);

}
