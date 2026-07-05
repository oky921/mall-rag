package com.example.ragdemo.dto;

import java.time.OffsetDateTime;

public class ErrorResponse {

    private final boolean success;

    private final String error;

    private final String message;

    private final String path;

    private final OffsetDateTime timestamp;

    private ErrorResponse(boolean success, String error, String message, String path, OffsetDateTime timestamp) {
        this.success = success;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = timestamp;
    }

    public static ErrorResponse of(String error, String message, String path) {
        return new ErrorResponse(false, error, message, path, OffsetDateTime.now());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}
