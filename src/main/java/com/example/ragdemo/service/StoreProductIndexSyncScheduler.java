package com.example.ragdemo.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store.search-index.sync-enabled", havingValue = "true", matchIfMissing = true)
public class StoreProductIndexSyncScheduler {

    private final StoreProductIndexEventProcessor processor;

    public StoreProductIndexSyncScheduler(StoreProductIndexEventProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.store.search-index.fixed-delay:5000}")
    public void synchronize() {
        processor.processDueBatch();
    }
}
