package com.example.ragdemo.service;

import com.example.ragdemo.store.StoreProduct;
import com.example.ragdemo.store.StoreProductRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StoreProductIndexEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(StoreProductIndexEventProcessor.class);

    private final StoreProductIndexEventClaimService claimService;
    private final StoreProductIndexEventStateService stateService;
    private final StoreProductRepository productRepository;
    private final StoreProductSearchIndexService indexService;

    public StoreProductIndexEventProcessor(StoreProductIndexEventClaimService claimService,
            StoreProductIndexEventStateService stateService, StoreProductRepository productRepository,
            StoreProductSearchIndexService indexService) {
        this.claimService = claimService;
        this.stateService = stateService;
        this.productRepository = productRepository;
        this.indexService = indexService;
    }

    public int processDueBatch() {
        List<StoreProductIndexEventClaimService.ClaimedEvent> events = claimService.claimDue(Instant.now());
        events.forEach(this::process);
        return events.size();
    }

    void process(StoreProductIndexEventClaimService.ClaimedEvent event) {
        try {
            StoreProduct product = productRepository.findByCode(event.productCode()).orElse(null);
            if (product != null && Boolean.TRUE.equals(product.getActive())) {
                indexService.upsert(product);
            } else {
                indexService.delete(event.productCode());
            }
            stateService.complete(event.id(), Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Product index event {} for {} failed.", event.id(), event.productCode(), ex);
            stateService.fail(event.id(), Instant.now(), ex);
        }
    }
}
