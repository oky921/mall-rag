package com.example.ragdemo.coupon;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

@Component
@Lazy(false)
public class CouponRabbitListener {
    private final CouponReceiveService service;
    public CouponRabbitListener(CouponReceiveService service) { this.service=service; }
    @RabbitListener(queues = CouponRabbitConfig.RECEIVE_QUEUE)
    public void receive(CouponRequest request) { service.persist(request); }
}
