package com.example.ragdemo.store;

import java.util.List;

public interface StoreCouponStrategy {
    List<StoreCouponCandidate> calculate(StoreCouponContext context, List<StoreUserCoupon> coupons);
}
