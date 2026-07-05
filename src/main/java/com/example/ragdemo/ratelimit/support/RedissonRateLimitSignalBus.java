package com.example.ragdemo.ratelimit.support;

import com.example.ragdemo.ratelimit.core.RateLimitSignalBus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedissonRateLimitSignalBus implements RateLimitSignalBus {

    private final RedissonClient redissonClient;

    private final ConcurrentMap<String, List<CountDownLatch>> waiters = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Integer> listenerIds = new ConcurrentHashMap<>();

    public RedissonRateLimitSignalBus(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void await(String channel, Duration timeout) throws InterruptedException {
        if (timeout.isZero() || timeout.isNegative()) {
            return;
        }
        ensureListener(channel);
        CountDownLatch latch = new CountDownLatch(1);
        List<CountDownLatch> channelWaiters = waiters.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>());
        channelWaiters.add(latch);
        try {
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            channelWaiters.remove(latch);
        }
    }

    private void ensureListener(String channel) {
        listenerIds.computeIfAbsent(channel, key -> {
            RTopic topic = redissonClient.getTopic(key, StringCodec.INSTANCE);
            return topic.addListener(String.class, (receivedChannel, message) -> signal(key));
        });
    }

    private void signal(String channel) {
        List<CountDownLatch> channelWaiters = waiters.get(channel);
        if (channelWaiters == null) {
            return;
        }
        for (CountDownLatch waiter : channelWaiters) {
            waiter.countDown();
        }
    }
}
