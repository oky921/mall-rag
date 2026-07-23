package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StoreCategoryDiscountCouponStrategy implements StoreCouponStrategy {
    @Override
    public List<StoreCouponCandidate> calculate(StoreCouponContext context, List<StoreUserCoupon> coupons) {
        return coupons.stream().filter(c -> c.getCoupon().getType() == StoreCoupon.Type.CATEGORY_DISCOUNT)
                .map(c -> {
                    StoreCoupon coupon = c.getCoupon();
                    BigDecimal eligible = StoreFullReductionCouponStrategy.eligibleAmount(context, coupon);
                    BigDecimal threshold = StoreFullReductionCouponStrategy.value(coupon.getThresholdAmount());
                    BigDecimal rate = StoreFullReductionCouponStrategy.value(coupon.getDiscountRate());
                    BigDecimal discount = eligible.compareTo(threshold) >= 0
                            ? eligible.multiply(BigDecimal.ONE.subtract(rate)) : BigDecimal.ZERO;
                    return new StoreCouponCandidate(c, discount.setScale(2, RoundingMode.DOWN));
                }).filter(c -> c.discountAmount().signum() > 0).toList();
    }
}
