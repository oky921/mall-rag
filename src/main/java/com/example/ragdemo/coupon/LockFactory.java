package com.example.ragdemo.coupon;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

interface LockStrategy {
    DistributedLock.LockType type();
    RLock create(String key);
}

@Component
public class LockFactory {
    private final Map<DistributedLock.LockType, LockStrategy> strategies = new EnumMap<>(DistributedLock.LockType.class);
    public LockFactory(List<LockStrategy> values) { values.forEach(v -> strategies.put(v.type(), v)); }
    public RLock create(DistributedLock.LockType type, String key) {
        LockStrategy strategy = strategies.get(type);
        if (strategy == null) throw new IllegalArgumentException("不支持的锁类型: " + type);
        return strategy.create(key);
    }
}

@Component class ReentrantLockStrategy implements LockStrategy {
    private final RedissonClient client; ReentrantLockStrategy(RedissonClient client){this.client=client;}
    public DistributedLock.LockType type(){return DistributedLock.LockType.REENTRANT;}
    public RLock create(String key){return client.getLock(key);}
}
@Component class FairLockStrategy implements LockStrategy {
    private final RedissonClient client; FairLockStrategy(RedissonClient client){this.client=client;}
    public DistributedLock.LockType type(){return DistributedLock.LockType.FAIR;}
    public RLock create(String key){return client.getFairLock(key);}
}
@Component class ReadLockStrategy implements LockStrategy {
    private final RedissonClient client; ReadLockStrategy(RedissonClient client){this.client=client;}
    public DistributedLock.LockType type(){return DistributedLock.LockType.READ;}
    public RLock create(String key){return client.getReadWriteLock(key).readLock();}
}
@Component class WriteLockStrategy implements LockStrategy {
    private final RedissonClient client; WriteLockStrategy(RedissonClient client){this.client=client;}
    public DistributedLock.LockType type(){return DistributedLock.LockType.WRITE;}
    public RLock create(String key){return client.getReadWriteLock(key).writeLock();}
}
