package com.example.ragdemo.store;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProductCatalogService {

    private final StoreProductRepository productRepository;
    private final StoreProductIndexOutboxRepository outboxRepository;

    public StoreProductCatalogService(StoreProductRepository productRepository,
            StoreProductIndexOutboxRepository outboxRepository) {
        this.productRepository = productRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public StoreProduct save(StoreProduct product) {
        StoreProduct saved = productRepository.save(product);
        append(saved.getCode(), StoreProductIndexEventType.UPSERT);
        return saved;
    }

    @Transactional
    public List<StoreProduct> saveAll(List<StoreProduct> products) {
        List<StoreProduct> saved = productRepository.saveAll(products);
        saved.forEach(product -> append(product.getCode(), StoreProductIndexEventType.UPSERT));
        return saved;
    }

    @Transactional
    public void deleteByCode(String productCode) {
        productRepository.findByCode(productCode).ifPresent(product -> {
            productRepository.delete(product);
            append(productCode, StoreProductIndexEventType.DELETE);
        });
    }

    @Transactional
    public StoreProduct setActive(String productCode, boolean active) {
        StoreProduct product = productRepository.findByCode(productCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product code: " + productCode));
        product.setActive(active);
        append(productCode, active ? StoreProductIndexEventType.UPSERT : StoreProductIndexEventType.DELETE);
        return product;
    }

    private void append(String productCode, StoreProductIndexEventType eventType) {
        outboxRepository.save(StoreProductIndexOutboxEvent.pending(productCode, eventType, Instant.now()));
    }
}
