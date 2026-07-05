package com.example.ragdemo.routing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class ModelCircuitBreaker {

    private final String modelId;

    private final int failureThreshold;

    private final Duration openDuration;

    private final Clock clock;

    private CircuitState state = CircuitState.CLOSED;

    private int consecutiveFailures;

    private Instant openedAt;

    private boolean probeInFlight;

    public ModelCircuitBreaker(String modelId, int failureThreshold, Duration openDuration, Clock clock) {
        this.modelId = modelId;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration == null ? Duration.ofSeconds(30) : openDuration;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public synchronized Permit acquirePermit() {
        refreshStateIfCooldownExpired();
        if (state == CircuitState.OPEN) {
            throw new ModelCircuitOpenException(modelId, state, remainingOpenDuration());
        }
        if (state == CircuitState.HALF_OPEN) {
            if (probeInFlight) {
                throw new ModelCircuitOpenException(modelId, state, Duration.ZERO);
            }
            probeInFlight = true;
            return new Permit(true);
        }
        return new Permit(false);
    }

    public synchronized void onSuccess(Permit permit) {
        consecutiveFailures = 0;
        probeInFlight = false;
        openedAt = null;
        state = CircuitState.CLOSED;
    }

    public synchronized void onFailure(Permit permit) {
        probeInFlight = false;
        consecutiveFailures++;
        if (state == CircuitState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = CircuitState.OPEN;
            openedAt = clock.instant();
        }
    }

    public synchronized Snapshot snapshot() {
        refreshStateIfCooldownExpired();
        return new Snapshot(state, consecutiveFailures, openedAt, remainingOpenDuration());
    }

    private void refreshStateIfCooldownExpired() {
        if (state != CircuitState.OPEN || openedAt == null) {
            return;
        }
        Instant now = clock.instant();
        if (!now.isBefore(openedAt.plus(openDuration))) {
            state = CircuitState.HALF_OPEN;
            probeInFlight = false;
        }
    }

    private Duration remainingOpenDuration() {
        if (state != CircuitState.OPEN || openedAt == null) {
            return Duration.ZERO;
        }
        Instant expiresAt = openedAt.plus(openDuration);
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public record Permit(boolean probe) {
    }

    public record Snapshot(CircuitState state, int consecutiveFailures, Instant openedAt, Duration remainingOpenDuration) {
    }
}
