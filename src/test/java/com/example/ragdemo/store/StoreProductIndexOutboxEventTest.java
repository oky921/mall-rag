package com.example.ragdemo.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StoreProductIndexOutboxEventTest {

    @Test
    void retriesWithExponentialBackoffThenFailsAtLimit() {
        Instant start = Instant.parse("2026-07-21T00:00:00Z");
        StoreProductIndexOutboxEvent event = StoreProductIndexOutboxEvent.pending(
                "P-1", StoreProductIndexEventType.UPSERT, start);

        event.fail(start, 3, Duration.ofSeconds(10), new IllegalStateException("milvus down"));
        assertThat(event.getStatus()).isEqualTo(StoreProductIndexEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isEqualTo(start.plusSeconds(10));

        event.fail(start, 3, Duration.ofSeconds(10), new IllegalStateException("milvus down"));
        assertThat(event.getNextRetryAt()).isEqualTo(start.plusSeconds(20));

        event.fail(start, 3, Duration.ofSeconds(10), new IllegalStateException("milvus down"));
        assertThat(event.getStatus()).isEqualTo(StoreProductIndexEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getLastError()).contains("milvus down");
    }
}
