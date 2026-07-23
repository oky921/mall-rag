package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.util.List;

public record StoreCouponPlan(List<StoreCouponCandidate> candidates,
        BigDecimal discountAmount, BigDecimal payableAmount) {
    public List<Long> userCouponIds() {
        return candidates.stream().map(candidate -> candidate.userCoupon().getId()).toList();
    }
}
