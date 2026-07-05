package com.example.ragdemo.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModelCircuitBreakerTest {

    @Test
    void shouldOpenAfterThresholdAndRecoverOnProbeSuccess() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-04T00:00:00Z"));
        Clock clock = Clock.fixed(now.get(), ZoneOffset.UTC);
        ModelCircuitBreaker breaker = new ModelCircuitBreaker("chat-primary", 2, Duration.ofSeconds(10),
                new MutableClock(now, clock.getZone()));

        breaker.onFailure(breaker.acquirePermit());
        assertEquals(CircuitState.CLOSED, breaker.snapshot().state());

        breaker.onFailure(breaker.acquirePermit());
        assertEquals(CircuitState.OPEN, breaker.snapshot().state());
        assertThrows(ModelCircuitOpenException.class, breaker::acquirePermit);

        now.set(now.get().plusSeconds(11));
        ModelCircuitBreaker.Permit permit = breaker.acquirePermit();
        assertEquals(CircuitState.HALF_OPEN, breaker.snapshot().state());
        breaker.onSuccess(permit);
        assertEquals(CircuitState.CLOSED, breaker.snapshot().state());
    }

    @Test
    void shouldReturnToOpenWhenHalfOpenProbeFails() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-04T00:00:00Z"));
        ModelCircuitBreaker breaker = new ModelCircuitBreaker("embedding-primary", 1, Duration.ofSeconds(5),
                new MutableClock(now, ZoneOffset.UTC));

        breaker.onFailure(breaker.acquirePermit());
        assertEquals(CircuitState.OPEN, breaker.snapshot().state());

        now.set(now.get().plusSeconds(6));
        ModelCircuitBreaker.Permit permit = breaker.acquirePermit();
        breaker.onFailure(permit);

        assertEquals(CircuitState.OPEN, breaker.snapshot().state());
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        private final java.time.ZoneId zoneId;

        private MutableClock(AtomicReference<Instant> now, java.time.ZoneId zoneId) {
            this.now = now;
            this.zoneId = zoneId;
        }

        @Override
        public java.time.ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return new MutableClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
