package com.example.ragdemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragdemo.config.StoreSearchIndexProperties;
import com.example.ragdemo.store.StoreProductIndexEventType;
import com.example.ragdemo.store.StoreProductIndexOutboxEvent;
import com.example.ragdemo.store.StoreProductIndexOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class StoreProductIndexEventClaimServiceTest {

    @Test
    void aClaimedEventIsNotReturnedToAnotherConsumerBeforeLeaseExpiry() {
        StoreProductIndexOutboxRepository repository = mock(StoreProductIndexOutboxRepository.class);
        StoreSearchIndexProperties properties = new StoreSearchIndexProperties();
        properties.setBatchSize(10);
        properties.setProcessingTimeout(Duration.ofMinutes(5));
        StoreProductIndexOutboxEvent event = StoreProductIndexOutboxEvent.pending(
                "P-1", StoreProductIndexEventType.UPSERT, Instant.parse("2026-07-21T00:00:00Z"));
        Instant now = Instant.parse("2026-07-21T00:01:00Z");
        when(repository.lockDue(org.mockito.ArgumentMatchers.eq(now),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(event))
                .thenReturn(List.of());
        StoreProductIndexEventClaimService firstConsumer =
                new StoreProductIndexEventClaimService(repository, properties);
        StoreProductIndexEventClaimService secondConsumer =
                new StoreProductIndexEventClaimService(repository, properties);

        assertThat(firstConsumer.claimDue(now)).hasSize(1);
        assertThat(secondConsumer.claimDue(now)).isEmpty();
        assertThat(event.getNextRetryAt()).isEqualTo(now.plus(Duration.ofMinutes(5)));
        verify(repository, org.mockito.Mockito.times(2)).flush();
    }
}
