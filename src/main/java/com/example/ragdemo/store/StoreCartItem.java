package com.example.ragdemo.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "store_cart_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_store_cart_user_sku", columnNames = {"user_id", "sku_id"}))
public class StoreCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false)
    private StoreSku sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected StoreCartItem() {
    }

    public StoreCartItem(Long userId, StoreSku sku, int quantity) {
        this.userId = userId;
        this.sku = sku;
        this.quantity = quantity;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public StoreSku getSku() { return sku; }
    public Integer getQuantity() { return quantity; }
    public Instant getUpdatedAt() { return updatedAt; }
}
