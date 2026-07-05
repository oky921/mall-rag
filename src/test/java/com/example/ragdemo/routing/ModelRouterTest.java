package com.example.ragdemo.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ragdemo.exception.AiServiceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelRouterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldFallbackToBackupWhenPrimaryFails() {
        ModelEndpoint<FakeModel> primary = new ModelEndpoint<>(
                "primary",
                "openai-compatible",
                100,
                true,
                ModelCapability.CHAT,
                new ModelCircuitBreaker("primary", 2, Duration.ofSeconds(30), clock),
                () -> {
                    throw new IllegalStateException("boom");
                });
        ModelEndpoint<FakeModel> backup = new ModelEndpoint<>(
                "backup",
                "openai-compatible",
                200,
                true,
                ModelCapability.CHAT,
                new ModelCircuitBreaker("backup", 2, Duration.ofSeconds(30), clock),
                () -> "ok-from-backup");

        String result = new ModelRouter().execute(ModelCapability.CHAT, List.of(primary, backup),
                endpoint -> endpoint.getDelegate().invoke());

        assertEquals("ok-from-backup", result);
        assertEquals(1, primary.getCircuitBreaker().snapshot().consecutiveFailures());
    }

    @Test
    void shouldFailWhenAllCandidatesUnavailable() {
        ModelEndpoint<FakeModel> primary = new ModelEndpoint<>(
                "primary",
                "openai-compatible",
                100,
                true,
                ModelCapability.EMBEDDING,
                new ModelCircuitBreaker("primary", 1, Duration.ofSeconds(30), clock),
                () -> {
                    throw new IllegalStateException("boom");
                });

        assertThrows(AiServiceException.class, () -> new ModelRouter().execute(
                ModelCapability.EMBEDDING,
                List.of(primary),
                endpoint -> endpoint.getDelegate().invoke()));
    }

    @FunctionalInterface
    private interface FakeModel {
        String invoke();
    }
}
