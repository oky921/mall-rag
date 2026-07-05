package com.example.ragdemo.ratelimit.core;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class RateLimitPermit implements AutoCloseable {

    private final String limiterName;

    private final String requestId;

    private final Instant acquiredAt;

    private final Runnable releaseAction;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RateLimitPermit(String limiterName, String requestId, Instant acquiredAt, Runnable releaseAction) {
        this.limiterName = limiterName;
        this.requestId = requestId;
        this.acquiredAt = acquiredAt;
        this.releaseAction = releaseAction;
    }

    public String limiterName() {
        return limiterName;
    }

    public String requestId() {
        return requestId;
    }

    public Instant acquiredAt() {
        return acquiredAt;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            releaseAction.run();
        }
    }
}
