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
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "store_user_coupons")
public class StoreUserCoupon {

    public static final String UNUSED = "UNUSED";
    public static final String USED = "USED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private StoreCoupon coupon;

    @Column(nullable = false, length = 20)
    private String status = UNUSED;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    private Instant usedAt;

    @Version
    private Long version;

    protected StoreUserCoupon() { }

    public StoreUserCoupon(Long userId, StoreCoupon coupon) {
        this.userId = userId;
        this.coupon = coupon;
    }

    public void use() {
        if (!UNUSED.equals(status)) {
            throw new IllegalStateException("优惠券不可用");
        }
        status = USED;
        usedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public StoreCoupon getCoupon() { return coupon; }
    public String getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getUsedAt() { return usedAt; }
}
