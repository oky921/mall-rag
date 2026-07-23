package com.example.ragdemo.store;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoreCouponService {
    private final StoreCouponRepository couponRepository;
    private final StoreUserCouponRepository userCouponRepository;
    private final CurrentStoreUser currentUser;

    public StoreCouponService(StoreCouponRepository couponRepository,
            StoreUserCouponRepository userCouponRepository, CurrentStoreUser currentUser) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<StoreApiModels.CouponResponse> findMine() {
        return userCouponRepository.findByUserIdAndStatus(currentUser.userId(), StoreUserCoupon.UNUSED)
                .stream().filter(this::usable).map(this::response).toList();
    }

    @Transactional
    public StoreApiModels.CouponResponse claim(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入优惠券编码");
        }
        StoreCoupon coupon = couponRepository.findByCode(code.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "优惠券不存在"));
        if (!usable(coupon)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "优惠券当前不可领取");
        }
        return response(userCouponRepository.save(new StoreUserCoupon(currentUser.userId(), coupon)));
    }

    private boolean usable(StoreCoupon coupon) {
        Instant now = Instant.now();
        return Boolean.TRUE.equals(coupon.getEnabled()) && !now.isBefore(coupon.getValidFrom())
                && !now.isAfter(coupon.getValidTo());
    }

    private boolean usable(StoreUserCoupon userCoupon) { return usable(userCoupon.getCoupon()); }

    private StoreApiModels.CouponResponse response(StoreUserCoupon userCoupon) {
        StoreCoupon coupon = userCoupon.getCoupon();
        return new StoreApiModels.CouponResponse(userCoupon.getId(), coupon.getCode(), coupon.getName(),
                coupon.getType().name(), coupon.getThresholdAmount(), coupon.getDiscountAmount(),
                coupon.getDiscountRate(), coupon.getStackable(), coupon.getProductId(), coupon.getCategory(),
                userCoupon.getStatus());
    }
}
