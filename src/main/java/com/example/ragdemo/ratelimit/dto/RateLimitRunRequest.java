package com.example.ragdemo.ratelimit.dto;

public class RateLimitRunRequest {

    private String requestId;

    private Long waitTimeoutMillis;

    private Long workMillis = 1000L;

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

    public Long getWorkMillis() {
        return workMillis;
    }

    public void setWorkMillis(Long workMillis) {
        this.workMillis = workMillis;
    }
}
