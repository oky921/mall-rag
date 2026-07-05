package com.example.ragdemo.ratelimit.core;

import java.time.Duration;
import java.util.function.Supplier;

public interface QueueRateLimiter {

    RateLimitPermit acquire(String limiterName, Duration waitTimeout);

    RateLimitPermit acquire(String limiterName, String requestId, Duration waitTimeout);

    ReleaseResult release(String limiterName, String requestId);

    ReleaseResult cancel(String limiterName, String requestId);

    RateLimitStatusSnapshot status(String limiterName, String requestId);

    <T> T execute(String limiterName, Duration waitTimeout, Supplier<T> supplier);
}
