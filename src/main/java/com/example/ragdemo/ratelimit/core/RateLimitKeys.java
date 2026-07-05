package com.example.ragdemo.ratelimit.core;

public record RateLimitKeys(
        String limiterName,
        String queueKey,
        String permitsKey,
        String requestKeyPrefix,
        String notifyChannel) {

    public static RateLimitKeys of(String keyPrefix, String limiterName) {
        String base = keyPrefix + ":{" + limiterName + "}";
        return new RateLimitKeys(
                limiterName,
                base + ":queue",
                base + ":permits",
                base + ":request:",
                base + ":notify");
    }

    public String requestKey(String requestId) {
        return requestKeyPrefix + requestId;
    }
}
