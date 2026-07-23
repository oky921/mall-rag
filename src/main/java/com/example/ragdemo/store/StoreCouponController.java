package com.example.ragdemo.store;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/coupons")
public class StoreCouponController {
    private final StoreCouponService service;

    public StoreCouponController(StoreCouponService service) { this.service = service; }

    @GetMapping
    public List<StoreApiModels.CouponResponse> mine() { return service.findMine(); }

    @PostMapping("/{code}/claim")
    public StoreApiModels.CouponResponse claim(@PathVariable String code) { return service.claim(code); }
}
