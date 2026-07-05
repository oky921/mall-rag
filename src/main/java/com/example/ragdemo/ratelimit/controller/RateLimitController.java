package com.example.ragdemo.ratelimit.controller;

import com.example.ragdemo.ratelimit.core.QueueRateLimiter;
import com.example.ragdemo.ratelimit.core.RateLimitPermit;
import com.example.ragdemo.ratelimit.core.RateLimitStatus;
import com.example.ragdemo.ratelimit.core.RateLimitStatusSnapshot;
import com.example.ragdemo.ratelimit.core.ReleaseResult;
import com.example.ragdemo.ratelimit.dto.RateLimitAcquireRequest;
import com.example.ragdemo.ratelimit.dto.RateLimitResponse;
import com.example.ragdemo.ratelimit.dto.RateLimitRunRequest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/ratelimit")
@ConditionalOnProperty(prefix = "app.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitController {

    private final QueueRateLimiter rateLimiter;

    public RateLimitController(QueueRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/{limiterName}/acquire")
    public RateLimitResponse acquire(@PathVariable String limiterName,
            @RequestBody(required = false) RateLimitAcquireRequest request) {
        RateLimitAcquireRequest effectiveRequest = request == null ? new RateLimitAcquireRequest() : request;
        String requestId = StringUtils.hasText(effectiveRequest.getRequestId())
                ? effectiveRequest.getRequestId()
                : UUID.randomUUID().toString();
        RateLimitPermit permit = rateLimiter.acquire(limiterName, requestId, duration(effectiveRequest.getWaitTimeoutMillis()));
        return RateLimitResponse.of(true, permit.limiterName(), permit.requestId(), RateLimitStatus.ACQUIRED,
                0, -1, "permit acquired; call release or cancel when finished");
    }

    @PostMapping("/{limiterName}/run")
    public RateLimitResponse run(@PathVariable String limiterName, @RequestBody(required = false) RateLimitRunRequest request) {
        RateLimitRunRequest effectiveRequest = request == null ? new RateLimitRunRequest() : request;
        String requestId = StringUtils.hasText(effectiveRequest.getRequestId())
                ? effectiveRequest.getRequestId()
                : UUID.randomUUID().toString();
        RateLimitPermit permit = rateLimiter.acquire(limiterName, requestId, duration(effectiveRequest.getWaitTimeoutMillis()));
        try {
            sleep(Math.max(0, effectiveRequest.getWorkMillis()));
            return RateLimitResponse.of(true, limiterName, requestId, RateLimitStatus.RELEASED,
                    0, -1, "work completed and permit released");
        } finally {
            permit.close();
        }
    }

    @PostMapping("/{limiterName}/release/{requestId}")
    public RateLimitResponse release(@PathVariable String limiterName, @PathVariable String requestId) {
        ReleaseResult result = rateLimiter.release(limiterName, requestId);
        return RateLimitResponse.of(true, limiterName, requestId, result.status(),
                -1, -1, result.permitReleased() ? "permit released" : "request was not holding a permit");
    }

    @PostMapping("/{limiterName}/cancel/{requestId}")
    public RateLimitResponse cancel(@PathVariable String limiterName, @PathVariable String requestId) {
        ReleaseResult result = rateLimiter.cancel(limiterName, requestId);
        return RateLimitResponse.of(true, limiterName, requestId, result.status(),
                -1, -1, result.queueRemoved() ? "queued request canceled" : "request canceled");
    }

    @GetMapping("/{limiterName}/status/{requestId}")
    public RateLimitResponse status(@PathVariable String limiterName, @PathVariable String requestId) {
        RateLimitStatusSnapshot snapshot = rateLimiter.status(limiterName, requestId);
        return RateLimitResponse.of(true, snapshot.limiterName(), snapshot.requestId(), snapshot.status(),
                snapshot.position(), snapshot.activePermits(), "status loaded");
    }

    private static Duration duration(Long millis) {
        return millis == null ? null : Duration.ofMillis(Math.max(1, millis));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating limited work", ex);
        }
    }
}
