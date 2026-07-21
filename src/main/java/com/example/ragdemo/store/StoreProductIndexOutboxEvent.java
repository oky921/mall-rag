package com.example.ragdemo.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "store_product_index_outbox", indexes = {
        @Index(name = "idx_product_outbox_claim", columnList = "status,nextRetryAt,createdAt"),
        @Index(name = "idx_product_outbox_code", columnList = "productCode,createdAt")
})
public class StoreProductIndexOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StoreProductIndexEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StoreProductIndexEventStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private Instant nextRetryAt;

    @Column(length = 2000)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant processedAt;

    protected StoreProductIndexOutboxEvent() {
    }

    private StoreProductIndexOutboxEvent(String productCode, StoreProductIndexEventType eventType, Instant now) {
        this.productCode = productCode;
        this.eventType = eventType;
        this.status = StoreProductIndexEventStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = now;
        this.createdAt = now;
    }

    public static StoreProductIndexOutboxEvent pending(
            String productCode, StoreProductIndexEventType eventType, Instant now) {
        return new StoreProductIndexOutboxEvent(productCode, eventType, now);
    }

    public void claim(Instant leaseUntil) {
        status = StoreProductIndexEventStatus.PROCESSING;
        nextRetryAt = leaseUntil;
    }

    public void complete(Instant now) {
        status = StoreProductIndexEventStatus.COMPLETED;
        processedAt = now;
        nextRetryAt = now;
        lastError = null;
    }

    public void fail(Instant now, int maxRetries, Duration initialBackoff, Throwable failure) {
        retryCount++;
        processedAt = null;
        lastError = truncate(failure);
        if (retryCount >= maxRetries) {
            status = StoreProductIndexEventStatus.FAILED;
            nextRetryAt = now;
            return;
        }
        status = StoreProductIndexEventStatus.PENDING;
        long exponent = Math.min(30, retryCount - 1L);
        long multiplier = 1L << exponent;
        Duration delay;
        try {
            delay = initialBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException ex) {
            delay = Duration.ofDays(365);
        }
        nextRetryAt = now.plus(delay);
    }

    private String truncate(Throwable failure) {
        String message = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "no message" : failure.getMessage());
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    public Long getId() { return id; }
    public String getProductCode() { return productCode; }
    public StoreProductIndexEventType getEventType() { return eventType; }
    public StoreProductIndexEventStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
