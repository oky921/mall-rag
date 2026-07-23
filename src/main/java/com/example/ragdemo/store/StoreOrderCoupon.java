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
@Table(name = "store_order_coupons")
public class StoreOrderCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StoreOrder order;

    @Column(name = "user_coupon_id", nullable = false)
    private Long userCouponId;

    @Column(nullable = false, length = 120)
    private String couponName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    protected StoreOrderCoupon() { }

    public StoreOrderCoupon(StoreUserCoupon userCoupon, BigDecimal discountAmount) {
        this.userCouponId = userCoupon.getId();
        this.couponName = userCoupon.getCoupon().getName();
        this.discountAmount = discountAmount;
    }

    void attachTo(StoreOrder order) { this.order = order; }
    public Long getId() { return id; }
    public Long getUserCouponId() { return userCouponId; }
    public String getCouponName() { return couponName; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
}
