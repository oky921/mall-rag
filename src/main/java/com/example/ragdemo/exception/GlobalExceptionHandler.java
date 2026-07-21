package com.example.ragdemo.exception;

import com.example.ragdemo.dto.ErrorResponse;
import com.example.ragdemo.ratelimit.exception.RateLimitCancelledException;
import com.example.ragdemo.ratelimit.exception.RateLimitTimeoutException;
import com.example.ragdemo.ratelimit.exception.RateLimitUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_JSON", "请求体不是合法 JSON", request);
    }

    @ExceptionHandler(AiConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleAiConfiguration(AiConfigurationException ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "AI_CONFIGURATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorResponse> handleAiService(AiServiceException ex, HttpServletRequest request) {
        log.warn("AI service call failed: {}", ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, "AI_SERVICE_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitTimeout(RateLimitTimeoutException ex,
            HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_TIMEOUT", ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitCancelledException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitCancelled(RateLimitCancelledException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "RATE_LIMIT_CANCELLED", ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitUnavailable(RateLimitUnavailableException ex,
            HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "接口不存在，请检查请求路径", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
        return build(status, status.name(), message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected request failure", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                ex.getClass().getSimpleName() + ": " + ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(error, message, request.getRequestURI()));
    }
}
