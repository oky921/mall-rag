package com.example.ragdemo.ratelimit.dto;

import com.example.ragdemo.ratelimit.core.RateLimitStatus;

public class RateLimitResponse {

    private final boolean success;

    private final String limiterName;

    private final String requestId;

    private final RateLimitStatus status;

    private final long position;

    private final long activePermits;

    private final String message;

    private RateLimitResponse(boolean success, String limiterName, String requestId, RateLimitStatus status,
            long position, long activePermits, String message) {
        this.success = success;
        this.limiterName = limiterName;
        this.requestId = requestId;
        this.status = status;
        this.position = position;
        this.activePermits = activePermits;
        this.message = message;
    }

    public static RateLimitResponse of(boolean success, String limiterName, String requestId,
            RateLimitStatus status, long position, long activePermits, String message) {
        return new RateLimitResponse(success, limiterName, requestId, status, position, activePermits, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getLimiterName() {
        return limiterName;
    }

    public String getRequestId() {
        return requestId;
    }

    public RateLimitStatus getStatus() {
        return status;
    }

    public long getPosition() {
        return position;
    }

    public long getActivePermits() {
        return activePermits;
    }

    public String getMessage() {
        return message;
    }
}
