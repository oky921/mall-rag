package com.example.ragdemo.ratelimit.core;

public enum RateLimitStatus {
    QUEUED,
    ACQUIRED,
    RELEASED,
    CANCELED,
    TIMED_OUT,
    MISSING
}
