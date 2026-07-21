package com.example.ragdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store.search-index.reconciliation-enabled", havingValue = "true")
public class StoreProductIndexReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(StoreProductIndexReconciliationScheduler.class);

    private final StoreProductSearchIndexService indexService;

    public StoreProductIndexReconciliationScheduler(StoreProductSearchIndexService indexService) {
        this.indexService = indexService;
    }

    @Scheduled(fixedDelayString = "${app.store.search-index.reconciliation-fixed-delay:6h}")
    public void reconcile() {
        try {
            indexService.reconcile();
        } catch (RuntimeException ex) {
            log.warn("Scheduled product index reconciliation failed.", ex);
        }
    }
}
