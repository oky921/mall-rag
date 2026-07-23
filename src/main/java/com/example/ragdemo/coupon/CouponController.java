package com.example.ragdemo.coupon;

import java.util.Map;
import java.time.Instant;
import java.util.List;
import com.example.ragdemo.store.StoreCouponRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupon")
public class CouponController {
    private final CouponReceiveService service; private final RabbitTemplate rabbit; private final StoreCouponRepository coupons;
    public CouponController(CouponReceiveService service, RabbitTemplate rabbit, StoreCouponRepository coupons) { this.service=service; this.rabbit=rabbit; this.coupons=coupons; }
    @GetMapping("/active")
    public List<CouponCampaignResponse> active() {
        Instant now = Instant.now();
        return coupons.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()) && !now.isBefore(c.getValidFrom()) && !now.isAfter(c.getValidTo()))
                .map(CouponCampaignResponse::from).toList();
    }
    @PostMapping("/receive") public Map<String,String> receive(@RequestBody CouponRequest req) { return Map.of("message", service.receive(req)); }
    @PostMapping("/seckill") public Map<String,String> seckill(@RequestBody CouponRequest req) {
        rabbit.convertAndSend(CouponRabbitConfig.SECKILL_QUEUE, req); return Map.of("message", "排队中");
    }
}
