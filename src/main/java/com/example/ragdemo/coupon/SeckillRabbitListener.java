package com.example.ragdemo.coupon;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

@Component
@Lazy(false)
public class SeckillRabbitListener {
    private final RedissonClient redisson; private final CouponReceiveService service;
    public SeckillRabbitListener(RedissonClient redisson, CouponReceiveService service) { this.redisson=redisson; this.service=service; }
    @RabbitListener(queues = CouponRabbitConfig.SECKILL_QUEUE)
    public void consume(CouponRequest req) {
        RLock lock=redisson.getLock("coupon:seckill:"+req.couponId());
        try { if(lock.tryLock(3,10,java.util.concurrent.TimeUnit.SECONDS)) service.seckill(req); }
        catch(Exception ignored) { }
        finally { if(lock.isHeldByCurrentThread()) lock.unlock(); }
    }
}
