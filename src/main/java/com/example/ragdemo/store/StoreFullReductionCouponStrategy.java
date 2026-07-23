package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StoreFullReductionCouponStrategy implements StoreCouponStrategy {
    @Override
    public List<StoreCouponCandidate> calculate(StoreCouponContext context, List<StoreUserCoupon> coupons) {
        return coupons.stream().filter(c -> c.getCoupon().getType() == StoreCoupon.Type.FULL_REDUCTION)
                .map(c -> {
                    StoreCoupon coupon = c.getCoupon();
                    BigDecimal eligible = eligibleAmount(context, coupon);
                    BigDecimal threshold = value(coupon.getThresholdAmount());
                    BigDecimal discount = eligible.compareTo(threshold) >= 0
                            ? value(coupon.getDiscountAmount()).min(eligible) : BigDecimal.ZERO;
                    return new StoreCouponCandidate(c, discount.setScale(2, RoundingMode.DOWN));
                }).filter(c -> c.discountAmount().signum() > 0).toList();
    }

    static BigDecimal eligibleAmount(StoreCouponContext context, StoreCoupon coupon) {
        return context.lines().stream().filter(line -> matches(line, coupon))
                .map(StoreCouponLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean matches(StoreCouponLine line, StoreCoupon coupon) {
        return (coupon.getProductId() == null || coupon.getProductId().equals(line.productId()))
                && (coupon.getCategory() == null || coupon.getCategory().equals(line.category()));
    }

    static BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
