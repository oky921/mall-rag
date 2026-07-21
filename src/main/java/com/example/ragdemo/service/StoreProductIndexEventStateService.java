package com.example.ragdemo.service;

import com.example.ragdemo.config.StoreSearchIndexProperties;
import com.example.ragdemo.store.StoreProductIndexOutboxEvent;
import com.example.ragdemo.store.StoreProductIndexOutboxRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProductIndexEventStateService {

    private final StoreProductIndexOutboxRepository repository;
    private final StoreSearchIndexProperties properties;

    public StoreProductIndexEventStateService(StoreProductIndexOutboxRepository repository,
            StoreSearchIndexProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(long eventId, Instant now) {
        repository.findById(eventId).ifPresent(event -> event.complete(now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(long eventId, Instant now, Throwable failure) {
        StoreProductIndexOutboxEvent event = repository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
        event.fail(now, Math.max(1, properties.getMaxRetries()), properties.getInitialBackoff(), failure);
    }
}
