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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "store_skus")
public class StoreSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private StoreProduct product;

    @Column(nullable = false, unique = true, length = 80)
    private String skuCode;

    @Column(nullable = false, length = 1000)
    private String specValues;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Integer sales;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected StoreSku() {
    }

    public StoreSku(StoreProduct product, String skuCode, String specValues, BigDecimal price,
            BigDecimal originalPrice, int stock, int sales, String imageUrl, boolean enabled) {
        this.product = product;
        this.skuCode = skuCode;
        this.specValues = specValues;
        this.price = price;
        this.originalPrice = originalPrice;
        this.stock = stock;
        this.sales = sales;
        this.imageUrl = imageUrl;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0 || stock < quantity) {
            throw new IllegalArgumentException("SKU库存不足");
        }
        stock -= quantity;
        sales += quantity;
        updatedAt = Instant.now();
    }

    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("恢复库存数量必须大于0");
        }
        stock += quantity;
        sales = Math.max(0, sales - quantity);
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public StoreProduct getProduct() { return product; }
    public String getSkuCode() { return skuCode; }
    public String getSpecValues() { return specValues; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public Integer getStock() { return stock; }
    public Integer getSales() { return sales; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getEnabled() { return enabled; }
}
