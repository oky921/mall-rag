package com.example.ragdemo.ratelimit.core;

public interface RateLimitRepository {

    void enqueue(RateLimitKeys keys, String requestId, long nowMillis, long deadlineMillis, long requestTtlMillis,
            String ownerId);

    TryAcquireResult tryAcquire(RateLimitKeys keys, String requestId, long nowMillis, int maxConcurrent,
            long permitExpireAtMillis, long requestTtlMillis, int cleanupBatchSize);

    ReleaseResult release(RateLimitKeys keys, String requestId, long nowMillis, long requestTtlMillis,
            RateLimitStatus finalStatus);

    RateLimitStatusSnapshot status(RateLimitKeys keys, String requestId);

    int cleanup(RateLimitKeys keys, long nowMillis, long requestTtlMillis, int cleanupBatchSize);
}
