package com.example.ragdemo.ratelimit.core;

import java.time.Duration;

public interface RateLimitSignalBus {

    void await(String channel, Duration timeout) throws InterruptedException;
}
