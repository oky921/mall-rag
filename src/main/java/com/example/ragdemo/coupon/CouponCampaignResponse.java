package com.example.ragdemo.coupon;

import com.example.ragdemo.store.StoreCoupon;
import java.time.Instant;

public record CouponCampaignResponse(Long id, String name, String code, String type,
        Integer stock, Integer total, Integer limitPerUser, Instant startTime, Instant endTime,
        java.math.BigDecimal thresholdAmount, java.math.BigDecimal discountAmount,
        java.math.BigDecimal discountRate, Long productId, String category) {
    public static CouponCampaignResponse from(StoreCoupon coupon) {
        int stock = coupon.getStock() == null ? 1000 : coupon.getStock();
        int total = coupon.getTotal() == null ? stock : coupon.getTotal();
        int limit = coupon.getLimitPerUser() == null ? 1 : coupon.getLimitPerUser();
        return new CouponCampaignResponse(coupon.getId(), coupon.getName(), coupon.getCode(),
                coupon.getType().name(), stock, total, limit,
                coupon.getValidFrom(), coupon.getValidTo(), coupon.getThresholdAmount(),
                coupon.getDiscountAmount(), coupon.getDiscountRate(), coupon.getProductId(), coupon.getCategory());
    }
}
