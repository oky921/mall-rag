package com.example.ragdemo.ratelimit.exception;

public class RateLimitTimeoutException extends RuntimeException {

    public RateLimitTimeoutException(String message) {
        super(message);
    }
}
