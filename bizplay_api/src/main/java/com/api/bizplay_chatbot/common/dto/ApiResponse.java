package com.api.bizplay_chatbot.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** Default success message when a caller doesn't supply one. */
    private static final String DEFAULT_OK_MESSAGE = "Success";

    private final boolean success;
    private final String message;
    private final T data;
    /** When this response was produced (server time, Asia/Seoul). */
    private final LocalDateTime createdAt;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.createdAt = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, DEFAULT_OK_MESSAGE, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message != null ? message : DEFAULT_OK_MESSAGE, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
