package com.example.ragdemo.coupon;

import com.example.ragdemo.store.StoreCouponRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CouponCacheWarmup {
    private final StoreCouponRepository coupons;
    private final CouponReceiveService service;
    public CouponCacheWarmup(StoreCouponRepository coupons, CouponReceiveService service) {
        this.coupons = coupons; this.service = service;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void warmAll() { coupons.findAll().forEach(service::warm); }
}
