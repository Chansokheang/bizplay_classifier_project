package com.api.bizplay_classifier_api.repository;

import com.api.bizplay_classifier_api.config.UUIDTypeHandler;
import com.api.bizplay_classifier_api.model.dto.AppUserDTO;
import com.api.bizplay_classifier_api.model.entity.AppUser;
import com.api.bizplay_classifier_api.model.request.AppUserRequest;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.util.UUID;

@Mapper
public interface AppUserRepo {

    @Select("""
        INSERT INTO "users" (username, firstname, lastname, email, password, gender, dob)
        VALUES (#{user.username}, #{user.firstname}, #{user.lastname}, #{user.email}, #{user.password},
                #{user.gender}, #{user.dob})
        RETURNING *
    """)
    @ResultMap("userMap")
    AppUser registerUser(@Param("user") AppUserRequest appUserRequest);

    @Select("""
        SELECT * FROM "users" WHERE email = #{email}
    """)
    @ResultMap("userMap")
    AppUser findUserByEmail(@Param("email") String email);

    @Select("""
        SELECT * FROM "users" WHERE user_id = #{userId}
    """)
    @Results(id = "userMap", value = {
            @Result(property = "userId",     column = "user_id",   jdbcType = JdbcType.OTHER,   typeHandler = UUIDTypeHandler.class),
            @Result(property = "roleId",     column = "role_id"),
            @Result(property = "username",   column = "username"),
            @Result(property = "firstname",  column = "firstname"),
            @Result(property = "lastname",   column = "lastname"),
            @Result(property = "email",      column = "email"),
            @Result(property = "password",   column = "password"),
            @Result(property = "gender",     column = "gender"),
            @Result(property = "dob",        column = "dob"),
            @Result(property = "isDisabled", column = "is_disabled"),
            @Result(property = "isVerified", column = "is_verified")
    })
    AppUser findUserByUserId(@Param("userId") UUID userId);
}
