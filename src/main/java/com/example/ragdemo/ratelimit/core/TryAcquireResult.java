package com.example.ragdemo.ratelimit.core;

public record TryAcquireResult(RateLimitStatus status, long position, long activePermits) {

    public boolean acquired() {
        return status == RateLimitStatus.ACQUIRED;
    }
}
