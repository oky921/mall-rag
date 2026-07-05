package com.example.ragdemo.ratelimit.exception;

public class RateLimitCancelledException extends RuntimeException {

    public RateLimitCancelledException(String message) {
        super(message);
    }
}
