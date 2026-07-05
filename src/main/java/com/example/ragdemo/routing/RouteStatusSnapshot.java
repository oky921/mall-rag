package com.example.ragdemo.routing;

import java.time.Duration;

public record RouteStatusSnapshot(
        String id,
        String provider,
        ModelCapability capability,
        int priority,
        boolean enabled,
        CircuitState state,
        int consecutiveFailures,
        Duration retryAfter) {
}
