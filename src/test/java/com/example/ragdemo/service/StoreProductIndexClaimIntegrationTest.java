package com.example.ragdemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragdemo.store.StoreProductIndexEventType;
import com.example.ragdemo.store.StoreProductIndexOutboxEvent;
import com.example.ragdemo.store.StoreProductIndexOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "app.store.search-index.batch-size=1",
        "app.store.search-index.processing-timeout=5m"
})
@Import(StoreProductIndexEventClaimService.class)
class StoreProductIndexClaimIntegrationTest {

    @Autowired StoreProductIndexOutboxRepository repository;
    @Autowired StoreProductIndexEventClaimService claimService;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentConsumersClaimAnEventOnlyOnce() throws Exception {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        requiresNew().executeWithoutResult(status -> repository.saveAndFlush(
                StoreProductIndexOutboxEvent.pending("LOCK-1", StoreProductIndexEventType.UPSERT, now)));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<List<StoreProductIndexEventClaimService.ClaimedEvent>> first =
                    executor.submit(() -> claimAfter(start, now));
            Future<List<StoreProductIndexEventClaimService.ClaimedEvent>> second =
                    executor.submit(() -> claimAfter(start, now));
            start.countDown();

            assertThat(first.get().size() + second.get().size()).isEqualTo(1);
        }
    }

    private List<StoreProductIndexEventClaimService.ClaimedEvent> claimAfter(
            CountDownLatch start, Instant now) throws InterruptedException {
        start.await();
        return claimService.claimDue(now);
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
