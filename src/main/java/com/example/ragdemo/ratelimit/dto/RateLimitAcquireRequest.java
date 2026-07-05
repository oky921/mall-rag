package com.example.ragdemo.ratelimit.dto;

public class RateLimitAcquireRequest {

    private String requestId;

    private Long waitTimeoutMillis;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getWaitTimeoutMillis() {
        return waitTimeoutMillis;
    }

    public void setWaitTimeoutMillis(Long waitTimeoutMillis) {
        this.waitTimeoutMillis = waitTimeoutMillis;
    }
}
