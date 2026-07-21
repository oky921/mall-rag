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

@Entity
@Table(name = "store_order_items")
public class StoreOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StoreOrder order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long skuId;

    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 80)
    private String skuCode;

    @Column(nullable = false, length = 1000)
    private String specValues;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    protected StoreOrderItem() {
    }

    public StoreOrderItem(StoreSku sku, int quantity) {
        this.productId = sku.getProduct().getId();
        this.skuId = sku.getId();
        this.productName = sku.getProduct().getName();
        this.skuCode = sku.getSkuCode();
        this.specValues = sku.getSpecValues();
        this.price = sku.getPrice();
        this.quantity = quantity;
        this.subtotal = sku.getPrice().multiply(BigDecimal.valueOf(quantity));
        this.imageUrl = sku.getImageUrl();
    }

    void attachTo(StoreOrder order) {
        this.order = order;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getSkuId() { return skuId; }
    public String getProductName() { return productName; }
    public String getSkuCode() { return skuCode; }
    public String getSpecValues() { return specValues; }
    public BigDecimal getPrice() { return price; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getSubtotal() { return subtotal; }
    public String getImageUrl() { return imageUrl; }
}
