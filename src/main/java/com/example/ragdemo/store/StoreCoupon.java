package com.example.ragdemo.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "store_coupons")
public class StoreCoupon {

    public enum Type { FULL_REDUCTION, DISCOUNT, CATEGORY_DISCOUNT, PRODUCT_FIXED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(precision = 12, scale = 2)
    private BigDecimal thresholdAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 5, scale = 4)
    private BigDecimal discountRate;

    @Column(nullable = false)
    private Boolean stackable;

    @Column
    private Long productId;

    @Column(length = 40)
    private String category;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validTo;

    @Column
    private Integer total = 1000;
    @Column
    private Integer stock = 1000;
    @Column
    private Integer issued = 0;
    @Column
    private Integer limitPerUser = 1;

    protected StoreCoupon() { }

    public StoreCoupon(String code, String name, Type type, BigDecimal thresholdAmount,
            BigDecimal discountAmount, BigDecimal discountRate, boolean stackable,
            Long productId, String category, Instant validFrom, Instant validTo) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.thresholdAmount = thresholdAmount;
        this.discountAmount = discountAmount;
        this.discountRate = discountRate;
        this.stackable = stackable;
        this.productId = productId;
        this.category = category;
        this.enabled = true;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public void configureInventory(int total, int limitPerUser) {
        this.total = total;
        this.stock = total;
        this.issued = 0;
        this.limitPerUser = limitPerUser;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public BigDecimal getThresholdAmount() { return thresholdAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public Boolean getStackable() { return stackable; }
    public Long getProductId() { return productId; }
    public String getCategory() { return category; }
    public Boolean getEnabled() { return enabled; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public Integer getTotal() { return total; }
    public Integer getStock() { return stock; }
    public Integer getIssued() { return issued; }
    public Integer getLimitPerUser() { return limitPerUser; }
}
