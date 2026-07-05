package com.example.ragdemo.ratelimit.support;

import com.example.ragdemo.ratelimit.core.RateLimitKeys;
import com.example.ragdemo.ratelimit.core.RateLimitRepository;
import com.example.ragdemo.ratelimit.core.RateLimitStatus;
import com.example.ragdemo.ratelimit.core.RateLimitStatusSnapshot;
import com.example.ragdemo.ratelimit.core.ReleaseResult;
import com.example.ragdemo.ratelimit.core.TryAcquireResult;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisLuaRateLimitRepository implements RateLimitRepository {

    private static final DefaultRedisScript<List> ENQUEUE_SCRIPT = script("""
            redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])
            redis.call('HSET', KEYS[2],
                'status', 'QUEUED',
                'enqueueTime', ARGV[2],
                'deadline', ARGV[3],
                'owner', ARGV[5],
                'updatedTime', ARGV[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            redis.call('PUBLISH', KEYS[3], 'ENQUEUED:' .. ARGV[1])
            return {'QUEUED', redis.call('ZRANK', KEYS[1], ARGV[1]) or -1, redis.call('HLEN', KEYS[4])}
            """);

    private static final DefaultRedisScript<List> TRY_ACQUIRE_SCRIPT = script("""
            local requestId = ARGV[1]
            local now = tonumber(ARGV[2])
            local maxConcurrent = tonumber(ARGV[3])
            local permitExpireAt = tonumber(ARGV[4])
            local requestTtl = tonumber(ARGV[5])
            local requestKeyPrefix = ARGV[6]
            local cleanupBatchSize = tonumber(ARGV[7])
            local released = 0

            local permits = redis.call('HGETALL', KEYS[2])
            for i = 1, #permits, 2 do
                if tonumber(permits[i + 1]) <= now then
                    redis.call('HDEL', KEYS[2], permits[i])
                    released = released + 1
                end
            end

            for i = 1, cleanupBatchSize do
                local head = redis.call('ZRANGE', KEYS[1], 0, 0)[1]
                if not head then
                    break
                end
                local headKey = requestKeyPrefix .. head
                local headStatus = redis.call('HGET', headKey, 'status')
                local headDeadline = tonumber(redis.call('HGET', headKey, 'deadline') or '0')
                if headStatus ~= 'QUEUED' or headDeadline <= now then
                    redis.call('ZREM', KEYS[1], head)
                    if headStatus == 'QUEUED' then
                        redis.call('HSET', headKey, 'status', 'TIMED_OUT', 'updatedTime', now)
                        redis.call('PEXPIRE', headKey, requestTtl)
                    end
                    released = released + 1
                else
                    break
                end
            end

            if released > 0 then
                redis.call('PUBLISH', KEYS[4], 'CLEANED:' .. released)
            end

            local status = redis.call('HGET', KEYS[3], 'status')
            if not status then
                return {'MISSING', -1, redis.call('HLEN', KEYS[2])}
            end
            if status ~= 'QUEUED' then
                return {status, -1, redis.call('HLEN', KEYS[2])}
            end

            local deadline = tonumber(redis.call('HGET', KEYS[3], 'deadline') or '0')
            if deadline <= now then
                redis.call('ZREM', KEYS[1], requestId)
                redis.call('HSET', KEYS[3], 'status', 'TIMED_OUT', 'updatedTime', now)
                redis.call('PEXPIRE', KEYS[3], requestTtl)
                redis.call('PUBLISH', KEYS[4], 'TIMED_OUT:' .. requestId)
                return {'TIMED_OUT', -1, redis.call('HLEN', KEYS[2])}
            end

            local head = redis.call('ZRANGE', KEYS[1], 0, 0)[1]
            local activePermits = redis.call('HLEN', KEYS[2])
            if head ~= requestId then
                local rank = redis.call('ZRANK', KEYS[1], requestId)
                if rank == false then
                    return {'MISSING', -1, activePermits}
                end
                return {'QUEUED', rank + 1, activePermits}
            end
            if activePermits >= maxConcurrent then
                return {'QUEUED', 1, activePermits}
            end

            redis.call('ZREM', KEYS[1], requestId)
            redis.call('HSET', KEYS[3],
                'status', 'ACQUIRED',
                'acquiredTime', now,
                'permitExpireAt', permitExpireAt,
                'updatedTime', now)
            redis.call('HSET', KEYS[2], requestId, permitExpireAt)
            redis.call('PEXPIRE', KEYS[3], requestTtl)
            redis.call('PUBLISH', KEYS[4], 'ACQUIRED:' .. requestId)
            return {'ACQUIRED', 0, activePermits + 1}
            """);

    private static final DefaultRedisScript<List> RELEASE_SCRIPT = script("""
            local requestId = ARGV[1]
            local now = ARGV[2]
            local requestTtl = ARGV[3]
            local finalStatus = ARGV[4]
            local permitReleased = redis.call('HDEL', KEYS[2], requestId)
            local queueRemoved = redis.call('ZREM', KEYS[1], requestId)
            if redis.call('EXISTS', KEYS[3]) == 1 then
                redis.call('HSET', KEYS[3], 'status', finalStatus, 'updatedTime', now)
                redis.call('PEXPIRE', KEYS[3], requestTtl)
            end
            redis.call('PUBLISH', KEYS[4], finalStatus .. ':' .. requestId)
            return {finalStatus, permitReleased, queueRemoved}
            """);

    private static final DefaultRedisScript<List> CLEANUP_SCRIPT = script("""
            local now = tonumber(ARGV[1])
            local requestTtl = tonumber(ARGV[2])
            local requestKeyPrefix = ARGV[3]
            local cleanupBatchSize = tonumber(ARGV[4])
            local cleaned = 0

            local permits = redis.call('HGETALL', KEYS[2])
            for i = 1, #permits, 2 do
                if tonumber(permits[i + 1]) <= now then
                    redis.call('HDEL', KEYS[2], permits[i])
                    cleaned = cleaned + 1
                end
            end

            for i = 1, cleanupBatchSize do
                local head = redis.call('ZRANGE', KEYS[1], 0, 0)[1]
                if not head then
                    break
                end
                local headKey = requestKeyPrefix .. head
                local status = redis.call('HGET', headKey, 'status')
                local deadline = tonumber(redis.call('HGET', headKey, 'deadline') or '0')
                if status ~= 'QUEUED' or deadline <= now then
                    redis.call('ZREM', KEYS[1], head)
                    if status == 'QUEUED' then
                        redis.call('HSET', headKey, 'status', 'TIMED_OUT', 'updatedTime', now)
                        redis.call('PEXPIRE', headKey, requestTtl)
                    end
                    cleaned = cleaned + 1
                else
                    break
                end
            end

            if cleaned > 0 then
                redis.call('PUBLISH', KEYS[3], 'CLEANED:' .. cleaned)
            end
            return {'CLEANED', cleaned, redis.call('HLEN', KEYS[2])}
            """);

    private final StringRedisTemplate redisTemplate;

    public RedisLuaRateLimitRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void enqueue(RateLimitKeys keys, String requestId, long nowMillis, long deadlineMillis,
            long requestTtlMillis, String ownerId) {
        redisTemplate.execute(ENQUEUE_SCRIPT, List.of(
                        keys.queueKey(),
                        keys.requestKey(requestId),
                        keys.notifyChannel(),
                        keys.permitsKey()),
                requestId,
                Long.toString(nowMillis),
                Long.toString(deadlineMillis),
                Long.toString(requestTtlMillis),
                ownerId);
    }

    @Override
    public TryAcquireResult tryAcquire(RateLimitKeys keys, String requestId, long nowMillis, int maxConcurrent,
            long permitExpireAtMillis, long requestTtlMillis, int cleanupBatchSize) {
        List<?> result = redisTemplate.execute(TRY_ACQUIRE_SCRIPT, List.of(
                        keys.queueKey(),
                        keys.permitsKey(),
                        keys.requestKey(requestId),
                        keys.notifyChannel()),
                requestId,
                Long.toString(nowMillis),
                Integer.toString(maxConcurrent),
                Long.toString(permitExpireAtMillis),
                Long.toString(requestTtlMillis),
                keys.requestKeyPrefix(),
                Integer.toString(cleanupBatchSize));
        return new TryAcquireResult(statusAt(result, 0), longAt(result, 1), longAt(result, 2));
    }

    @Override
    public ReleaseResult release(RateLimitKeys keys, String requestId, long nowMillis, long requestTtlMillis,
            RateLimitStatus finalStatus) {
        List<?> result = redisTemplate.execute(RELEASE_SCRIPT, List.of(
                        keys.queueKey(),
                        keys.permitsKey(),
                        keys.requestKey(requestId),
                        keys.notifyChannel()),
                requestId,
                Long.toString(nowMillis),
                Long.toString(requestTtlMillis),
                finalStatus.name());
        return new ReleaseResult(statusAt(result, 0), longAt(result, 1) > 0, longAt(result, 2) > 0);
    }

    @Override
    public RateLimitStatusSnapshot status(RateLimitKeys keys, String requestId) {
        String requestKey = keys.requestKey(requestId);
        Object status = redisTemplate.opsForHash().get(requestKey, "status");
        Long rank = redisTemplate.opsForZSet().rank(keys.queueKey(), requestId);
        Long activePermits = redisTemplate.opsForHash().size(keys.permitsKey());
        RateLimitStatus rateLimitStatus = status == null ? RateLimitStatus.MISSING : RateLimitStatus.valueOf(status.toString());
        long position = rank == null ? -1 : rank + 1;
        return new RateLimitStatusSnapshot(keys.limiterName(), requestId, rateLimitStatus, position,
                activePermits == null ? 0 : activePermits);
    }

    @Override
    public int cleanup(RateLimitKeys keys, long nowMillis, long requestTtlMillis, int cleanupBatchSize) {
        List<?> result = redisTemplate.execute(CLEANUP_SCRIPT, List.of(
                        keys.queueKey(),
                        keys.permitsKey(),
                        keys.notifyChannel()),
                Long.toString(nowMillis),
                Long.toString(requestTtlMillis),
                keys.requestKeyPrefix(),
                Integer.toString(cleanupBatchSize));
        return (int) longAt(result, 1);
    }

    private static DefaultRedisScript<List> script(String source) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(List.class);
        return script;
    }

    private static RateLimitStatus statusAt(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return RateLimitStatus.MISSING;
        }
        return RateLimitStatus.valueOf(result.get(index).toString());
    }

    private static long longAt(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return -1;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
