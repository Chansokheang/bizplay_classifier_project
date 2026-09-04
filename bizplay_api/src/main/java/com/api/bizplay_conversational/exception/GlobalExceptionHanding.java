package com.api.bizplay_conversational.exception;

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

@RestControllerAdvice(basePackages = "com.api.bizplay_conversational")
public class GlobalExceptionHanding {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHanding.class);

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = CustomNotFoundException.class)
    public ProblemDetail handlerAllNotFoundException(CustomNotFoundException e){
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());

        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle("Not Found");
        problemDetail.setStatus(404);
        problemDetail.setDetail(e.getMessage());
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handlerArgumentNotValidException(MethodArgumentNotValidException e){
        HashMap<String, String> errors = new HashMap<>();
        for(FieldError fieldError : e.getBindingResult().getFieldErrors()){
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid Request");
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", LocalDateTime.now());

        return problemDetail;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidationException(HandlerMethodValidationException e) {
        HashMap<String, String> errors = new HashMap<>();

        for (var validationResult : e.getParameterValidationResults()) {
            String parameterName = validationResult.getMethodParameter().getParameterName();

            for (var resolvableError : validationResult.getResolvableErrors()) {
                String message = resolvableError.getDefaultMessage();
                errors.put(parameterName, message != null ? message : "Validation failed");
            }
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid request"
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("error", errors);
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed request body."
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("error", e.getMostSpecificCause().getMessage());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                e.getMessage() != null ? e.getMessage() : "Invalid request."
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(IllegalStateException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                e.getMessage() != null ? e.getMessage() : "Service is temporarily unavailable."
        );
        problemDetail.setTitle("Service Unavailable");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKeyException(DuplicateKeyException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Duplicate value violates a unique constraint."
        );
        problemDetail.setTitle("Conflict");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }

    /**
     * A constraint the request itself violated — the one that used to surface as a bare 500 was a
     * conversational session pointing at a corpNo with no {@code corp} row. The caller can act on
     * that, so it is answered as a 400 that names the value, not as a server error.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException e) {
        String cause = rootMessage(e);
        boolean unknownCorp = cause.contains("corp_no") || cause.contains("corp_no_fkey");
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                unknownCorp
                        ? "corpNo is not registered for this service and could not be registered "
                                + "automatically. Register it first (POST /api/v1/corp) or send the "
                                + "corporation's 10-digit business registration number."
                        : "The request violates a database constraint: " + cause);
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("error", cause);
        return problemDetail;
    }

    /** A path variable or query parameter of the wrong type — the caller's mistake, so 400. */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() == null ? "the expected type"
                : e.getRequiredType().getSimpleName();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "'" + e.getName() + "' must be " + expected + " — received \"" + e.getValue() + "\".");
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }

    /** A required query parameter (corpNo, sessionId…) was left out. */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(
            org.springframework.web.bind.MissingServletRequestParameterException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "'" + e.getParameterName() + "' is required.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }

    /**
     * The last resort. Spring's default 500 body carries no reason at all, which left the caller
     * with nothing to report but "it 500s" — so anything unhandled still answers 500 (it IS our
     * fault), but with the cause and an id that matches the log line.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        String errorId = java.util.UUID.randomUUID().toString().substring(0, 8);
        LOG.error("Unhandled exception [{}] in the conversational module", errorId, e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getClass().getSimpleName() + ": " + rootMessage(e));
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("errorId", errorId);
        return problemDetail;
    }

    /** The innermost message — the outer wrappers say "Error updating database", the cause says why. */
    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return message.length() > 400 ? message.substring(0, 400) + "…" : message;
    }
}
