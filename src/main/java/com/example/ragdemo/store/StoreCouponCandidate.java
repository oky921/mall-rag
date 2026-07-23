package com.example.ragdemo.store;

import java.math.BigDecimal;

public record StoreCouponCandidate(StoreUserCoupon userCoupon, BigDecimal discountAmount) { }
