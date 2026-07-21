package com.example.ragdemo.service;

import com.example.ragdemo.config.StoreSearchIndexProperties;
import com.example.ragdemo.store.StoreProductIndexEventType;
import com.example.ragdemo.store.StoreProductIndexOutboxEvent;
import com.example.ragdemo.store.StoreProductIndexOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProductIndexEventClaimService {

    private final StoreProductIndexOutboxRepository repository;
    private final StoreSearchIndexProperties properties;

    public StoreProductIndexEventClaimService(StoreProductIndexOutboxRepository repository,
            StoreSearchIndexProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedEvent> claimDue(Instant now) {
        int batchSize = Math.max(1, properties.getBatchSize());
        Instant leaseUntil = now.plus(properties.getProcessingTimeout());
        List<StoreProductIndexOutboxEvent> events = repository.lockDue(now, PageRequest.of(0, batchSize));
        events.forEach(event -> event.claim(leaseUntil));
        repository.flush();
        return events.stream()
                .map(event -> new ClaimedEvent(event.getId(), event.getProductCode(), event.getEventType()))
                .toList();
    }

    public record ClaimedEvent(Long id, String productCode, StoreProductIndexEventType eventType) {
    }
}
