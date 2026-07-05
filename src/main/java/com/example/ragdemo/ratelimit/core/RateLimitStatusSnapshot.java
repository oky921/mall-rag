package com.example.ragdemo.ratelimit.core;

public record RateLimitStatusSnapshot(
        String limiterName,
        String requestId,
        RateLimitStatus status,
        long position,
        long activePermits) {
}
