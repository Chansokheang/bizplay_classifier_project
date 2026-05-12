package com.api.bizplay_compliance.exception;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;

@RestControllerAdvice
public class GlobalExceptionHandling {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CustomNotFoundException.class)
    public ProblemDetail handleNotFoundException(CustomNotFoundException exception) {
        return buildProblemDetail(
                HttpStatus.NOT_FOUND,
                "Not Found",
                exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleArgumentNotValidException(MethodArgumentNotValidException exception) {
        HashMap<String, String> errors = new HashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Invalid request"
        );
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidationException(HandlerMethodValidationException exception) {
        HashMap<String, String> errors = new HashMap<>();

        for (var validationResult : exception.getParameterValidationResults()) {
            String parameterName = validationResult.getMethodParameter().getParameterName();

            for (var resolvableError : validationResult.getResolvableErrors()) {
                String message = resolvableError.getDefaultMessage();
                errors.put(parameterName, message != null ? message : "Validation failed");
            }
        }

        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Invalid request"
        );
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Malformed request body."
        );
        problemDetail.setProperty("errors", exception.getMostSpecificCause().getMessage());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException exception) {
        return buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                exception.getMessage() != null ? exception.getMessage() : "Invalid request."
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(IllegalStateException exception) {
        return buildProblemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                exception.getMessage() != null ? exception.getMessage() : "Service is temporarily unavailable."
        );
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKeyException(DuplicateKeyException exception) {
        return buildProblemDetail(
                HttpStatus.CONFLICT,
                "Conflict",
                "Duplicate value violates a unique constraint."
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandledException(Exception exception) {
        return buildProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                exception.getMessage() != null ? exception.getMessage() : "An unexpected error occurred."
        );
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle(title);
        problemDetail.setStatus(status.value());
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }
}

