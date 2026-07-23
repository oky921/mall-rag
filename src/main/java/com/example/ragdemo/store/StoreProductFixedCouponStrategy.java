package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StoreProductFixedCouponStrategy implements StoreCouponStrategy {
    @Override
    public List<StoreCouponCandidate> calculate(StoreCouponContext context, List<StoreUserCoupon> coupons) {
        return coupons.stream().filter(c -> c.getCoupon().getType() == StoreCoupon.Type.PRODUCT_FIXED)
                .map(c -> {
                    StoreCoupon coupon = c.getCoupon();
                    BigDecimal eligible = StoreFullReductionCouponStrategy.eligibleAmount(context, coupon);
                    BigDecimal threshold = StoreFullReductionCouponStrategy.value(coupon.getThresholdAmount());
                    BigDecimal discount = eligible.compareTo(threshold) >= 0
                            ? StoreFullReductionCouponStrategy.value(coupon.getDiscountAmount()).min(eligible)
                            : BigDecimal.ZERO;
                    return new StoreCouponCandidate(c, discount.setScale(2, RoundingMode.DOWN));
                }).filter(c -> c.discountAmount().signum() > 0).toList();
    }
}
