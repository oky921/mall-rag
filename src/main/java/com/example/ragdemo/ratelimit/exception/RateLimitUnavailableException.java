package com.example.ragdemo.ratelimit.exception;

public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException(String message) {
        super(message);
    }

    public RateLimitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
