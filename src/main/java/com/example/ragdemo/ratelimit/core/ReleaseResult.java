package com.example.ragdemo.ratelimit.core;

public record ReleaseResult(RateLimitStatus status, boolean permitReleased, boolean queueRemoved) {
}
