package com.example.ragdemo.store;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "store_orders")
public class StoreOrder {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "decimal(12,2) default 0.00")
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "decimal(12,2) default 0.00")
    private BigDecimal payableAmount;

    @Column(nullable = false, length = 40)
    private String receiverName;

    @Column(nullable = false, length = 30)
    private String receiverPhone;

    @Column(nullable = false, length = 300)
    private String receiverAddress;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(unique = true, length = 50)
    private String paymentNo;

    private Instant paidAt;

    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StoreOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StoreOrderCoupon> coupons = new ArrayList<>();

    protected StoreOrder() {
    }

    public StoreOrder(String orderNo, Long userId, String receiverName, String receiverPhone,
            String receiverAddress) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.status = STATUS_CREATED;
        this.totalAmount = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.payableAmount = BigDecimal.ZERO;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverAddress = receiverAddress;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(StoreOrderItem item) {
        items.add(item);
        item.attachTo(this);
        totalAmount = totalAmount.add(item.getSubtotal());
        payableAmount = totalAmount.subtract(discountAmount);
        updatedAt = Instant.now();
    }

    public void addCoupon(StoreOrderCoupon coupon) {
        coupons.add(coupon);
        coupon.attachTo(this);
        discountAmount = discountAmount.add(coupon.getDiscountAmount());
        payableAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        updatedAt = Instant.now();
    }

    public void pay(String paymentNo) {
        if (STATUS_PAID.equals(status)) {
            return;
        }
        if (!STATUS_CREATED.equals(status)) {
            throw new IllegalStateException("当前订单状态不允许支付");
        }
        this.status = STATUS_PAID;
        this.paymentNo = paymentNo;
        this.paidAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.paidAt;
    }

    public boolean cancel() {
        if (STATUS_CANCELLED.equals(status)) {
            return false;
        }
        if (!STATUS_CREATED.equals(status)) {
            throw new IllegalStateException("当前订单状态不允许取消");
        }
        this.status = STATUS_CANCELLED;
        this.cancelledAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.cancelledAt;
        return true;
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getPayableAmount() { return payableAmount; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public String getReceiverAddress() { return receiverAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getPaymentNo() { return paymentNo; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public List<StoreOrderItem> getItems() { return Collections.unmodifiableList(items); }
    public List<StoreOrderCoupon> getCoupons() { return Collections.unmodifiableList(coupons); }
}
