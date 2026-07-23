package com.example.ragdemo.coupon;

import com.example.ragdemo.store.StoreCoupon;
import com.example.ragdemo.store.StoreCouponRepository;
import com.example.ragdemo.store.StoreUserCoupon;
import com.example.ragdemo.store.StoreUserCouponRepository;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponReceiveService {
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('HGET', KEYS[1], 'stock'))
            if not stock or stock <= 0 then return 1 end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 2 end
            redis.call('HINCRBY', KEYS[1], 'stock', -1)
            redis.call('SADD', KEYS[2], ARGV[1])
            return 0
            """, Long.class);
    private final StoreCouponRepository coupons; private final StoreUserCouponRepository userCoupons;
    private final StringRedisTemplate redis; private final RabbitTemplate rabbit;
    public CouponReceiveService(StoreCouponRepository coupons, StoreUserCouponRepository userCoupons,
            StringRedisTemplate redis, RabbitTemplate rabbit) { this.coupons=coupons; this.userCoupons=userCoupons; this.redis=redis; this.rabbit=rabbit; }

    public void warm(StoreCoupon c) {
        if (Boolean.TRUE.equals(redis.hasKey("coupon:" + c.getId()))) return;
        int stock = c.getStock() == null ? 1000 : c.getStock();
        int total = c.getTotal() == null ? stock : c.getTotal();
        int limit = c.getLimitPerUser() == null ? 1 : c.getLimitPerUser();
        redis.opsForHash().putAll("coupon:"+c.getId(), Map.of("stock", String.valueOf(stock), "total", String.valueOf(total),
                "startTime", c.getValidFrom().toString(), "endTime", c.getValidTo().toString(), "limitPerUser", String.valueOf(limit)));
    }
    public String receive(CouponRequest request) {
        validate(request);
        StoreCoupon c = coupons.findById(request.couponId()).orElseThrow(() -> new IllegalArgumentException("优惠券不存在"));
        warm(c);
        Map<Object,Object> h = redis.opsForHash().entries("coupon:"+c.getId());
        Instant now=Instant.now(); if (now.isBefore(c.getValidFrom())||now.isAfter(c.getValidTo())) throw new IllegalStateException("不在发放时间");
        if (Integer.parseInt(String.valueOf(h.get("stock"))) <= 0) throw new IllegalStateException("优惠券已抢光");
        String countKey="coupon:user:count:"+c.getId(); Object count=redis.opsForHash().get(countKey, String.valueOf(request.userId()));
        int limit = c.getLimitPerUser() == null ? 1 : c.getLimitPerUser();
        if (count != null && Integer.parseInt(String.valueOf(count)) >= limit) throw new IllegalStateException("超过限领次数");
        redis.opsForHash().increment(countKey, String.valueOf(request.userId()), 1);
        try { rabbit.convertAndSend(CouponRabbitConfig.RECEIVE_QUEUE, request); }
        catch (RuntimeException e) {
            redis.opsForHash().increment(countKey, String.valueOf(request.userId()), -1);
            throw e;
        }
        return "领取成功，处理中";
    }

    public void seckill(CouponRequest request) {
        validate(request);
        StoreCoupon c = coupons.findById(request.couponId()).orElseThrow(() -> new IllegalArgumentException("优惠券不存在"));
        Instant now = Instant.now();
        if (now.isBefore(c.getValidFrom()) || now.isAfter(c.getValidTo())) throw new IllegalStateException("不在发放时间");
        warm(c);
        Long result = redis.execute(SECKILL_SCRIPT,
                List.of("coupon:" + c.getId(), "coupon:seckill:users:" + c.getId()), String.valueOf(request.userId()));
        if (result == null || result == 1) throw new IllegalStateException("优惠券已抢光");
        if (result == 2) throw new IllegalStateException("请勿重复抢券");
        try { rabbit.convertAndSend(CouponRabbitConfig.RECEIVE_QUEUE, request); }
        catch (RuntimeException e) {
            redis.opsForHash().increment("coupon:" + c.getId(), "stock", 1);
            redis.opsForSet().remove("coupon:seckill:users:" + c.getId(), String.valueOf(request.userId()));
            throw e;
        }
    }

    private void validate(CouponRequest request) {
        if (request == null || request.userId() == null || request.couponId() == null
                || request.userId() <= 0 || request.couponId() <= 0) throw new IllegalArgumentException("userId 和 couponId 必须为正数");
    }

    @Transactional
    public void persist(CouponRequest req) {
        StoreCoupon c=coupons.findById(req.couponId()).orElse(null); if(c==null) return;
        long received = userCoupons.countByUserIdAndCouponId(req.userId(), c.getId());
        int limit = c.getLimitPerUser() == null ? 1 : c.getLimitPerUser();
        if (received >= limit || coupons.decrementStock(c.getId())==0) return;
        userCoupons.save(new StoreUserCoupon(req.userId(), c));
    }
}
