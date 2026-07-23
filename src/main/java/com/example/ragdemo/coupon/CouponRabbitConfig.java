package com.example.ragdemo.coupon;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
@Lazy(false)
public class CouponRabbitConfig {
    public static final String RECEIVE_QUEUE = "coupon.receive.queue";
    public static final String SECKILL_QUEUE = "seckill.request.queue";
    @Bean Queue couponReceiveQueue() { return new Queue(RECEIVE_QUEUE, true); }
    @Bean Queue seckillQueue() { return new Queue(SECKILL_QUEUE, true); }
    @Bean MessageConverter couponMessageConverter() { return new JacksonJsonMessageConverter(); }
}
