package com.example.ragdemo.ratelimit.core;

import com.example.ragdemo.ratelimit.config.RateLimitProperties;
import com.example.ragdemo.ratelimit.config.RateLimitProperties.LimitPolicy;
import com.example.ragdemo.ratelimit.exception.RateLimitCancelledException;
import com.example.ragdemo.ratelimit.exception.RateLimitTimeoutException;
import com.example.ragdemo.ratelimit.exception.RateLimitUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "app.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DistributedQueueRateLimiter implements QueueRateLimiter {

    private final RateLimitProperties properties;

    private final RateLimitRepository repository;

    private final RateLimitSignalBus signalBus;

    private final Clock clock;

    private final Set<String> activeLimiterNames = ConcurrentHashMap.newKeySet();

    @Autowired
    public DistributedQueueRateLimiter(RateLimitProperties properties, RateLimitRepository repository,
            RateLimitSignalBus signalBus) {
        this(properties, repository, signalBus, Clock.systemUTC());
    }

    DistributedQueueRateLimiter(RateLimitProperties properties, RateLimitRepository repository,
            RateLimitSignalBus signalBus, Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.signalBus = signalBus;
        this.clock = clock;
    }

    @Override
    public RateLimitPermit acquire(String limiterName, Duration waitTimeout) {
        return acquire(limiterName, UUID.randomUUID().toString(), waitTimeout);
    }

    @Override
    public RateLimitPermit acquire(String limiterName, String requestId, Duration waitTimeout) {
        String normalizedLimiterName = normalizeLimiterName(limiterName);
        String normalizedRequestId = normalizeRequestId(requestId);
        activeLimiterNames.add(normalizedLimiterName);

        LimitPolicy policy = properties.policyFor(normalizedLimiterName);
        Duration effectiveWaitTimeout = durationOrDefault(waitTimeout, policy.getWaitTimeout());
        long now = nowMillis();
        long deadline = now + effectiveWaitTimeout.toMillis();
        RateLimitKeys keys = keys(normalizedLimiterName);

        repository.enqueue(keys, normalizedRequestId, now, deadline, policy.getRequestTtl().toMillis(),
                properties.getOwnerId());

        try {
            while (true) {
                now = nowMillis();
                if (now >= deadline) {
                    repository.release(keys, normalizedRequestId, now, policy.getRequestTtl().toMillis(),
                            RateLimitStatus.TIMED_OUT);
                    throw new RateLimitTimeoutException("Rate limit queue wait timed out: " + normalizedRequestId);
                }

                TryAcquireResult result = repository.tryAcquire(
                        keys,
                        normalizedRequestId,
                        now,
                        Math.max(1, policy.getMaxConcurrent()),
                        now + policy.getPermitTtl().toMillis(),
                        policy.getRequestTtl().toMillis(),
                        Math.max(1, policy.getCleanupBatchSize()));

                if (result.acquired()) {
                    Instant acquiredAt = Instant.ofEpochMilli(now);
                    return new RateLimitPermit(normalizedLimiterName, normalizedRequestId, acquiredAt,
                            () -> release(normalizedLimiterName, normalizedRequestId));
                }
                if (result.status() == RateLimitStatus.TIMED_OUT) {
                    throw new RateLimitTimeoutException("Rate limit queue wait timed out: " + normalizedRequestId);
                }
                if (result.status() == RateLimitStatus.CANCELED) {
                    throw new RateLimitCancelledException("Rate limit request was canceled: " + normalizedRequestId);
                }
                if (result.status() == RateLimitStatus.MISSING) {
                    throw new RateLimitUnavailableException("Rate limit request disappeared: " + normalizedRequestId);
                }

                long remainingMillis = deadline - nowMillis();
                Duration waitSlice = Duration.ofMillis(Math.max(1,
                        Math.min(policy.getPubSubWait().toMillis(), remainingMillis)));
                signalBus.await(keys.notifyChannel(), waitSlice);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            repository.release(keys, normalizedRequestId, nowMillis(), policy.getRequestTtl().toMillis(),
                    RateLimitStatus.CANCELED);
            throw new RateLimitCancelledException("Rate limit wait was interrupted: " + normalizedRequestId);
        } catch (RuntimeException ex) {
            if (!(ex instanceof RateLimitTimeoutException)
                    && !(ex instanceof RateLimitCancelledException)
                    && !(ex instanceof RateLimitUnavailableException)) {
                repository.release(keys, normalizedRequestId, nowMillis(), policy.getRequestTtl().toMillis(),
                        RateLimitStatus.CANCELED);
            }
            throw ex;
        }
    }

    @Override
    public ReleaseResult release(String limiterName, String requestId) {
        String normalizedLimiterName = normalizeLimiterName(limiterName);
        LimitPolicy policy = properties.policyFor(normalizedLimiterName);
        return repository.release(keys(normalizedLimiterName), normalizeRequestId(requestId), nowMillis(),
                policy.getRequestTtl().toMillis(), RateLimitStatus.RELEASED);
    }

    @Override
    public ReleaseResult cancel(String limiterName, String requestId) {
        String normalizedLimiterName = normalizeLimiterName(limiterName);
        LimitPolicy policy = properties.policyFor(normalizedLimiterName);
        return repository.release(keys(normalizedLimiterName), normalizeRequestId(requestId), nowMillis(),
                policy.getRequestTtl().toMillis(), RateLimitStatus.CANCELED);
    }

    @Override
    public RateLimitStatusSnapshot status(String limiterName, String requestId) {
        String normalizedLimiterName = normalizeLimiterName(limiterName);
        return repository.status(keys(normalizedLimiterName), normalizeRequestId(requestId));
    }

    @Override
    public <T> T execute(String limiterName, Duration waitTimeout, Supplier<T> supplier) {
        try (RateLimitPermit ignored = acquire(limiterName, waitTimeout)) {
            return supplier.get();
        }
    }

    @Scheduled(fixedDelayString = "${app.ratelimit.defaults.cleanup-interval:10s}")
    public void cleanupActiveLimiters() {
        for (String limiterName : activeLimiterNames) {
            LimitPolicy policy = properties.policyFor(limiterName);
            repository.cleanup(keys(limiterName), nowMillis(), policy.getRequestTtl().toMillis(),
                    Math.max(1, policy.getCleanupBatchSize()));
        }
    }

    private RateLimitKeys keys(String limiterName) {
        return RateLimitKeys.of(properties.getKeyPrefix(), limiterName);
    }

    private long nowMillis() {
        return clock.millis();
    }

    private static Duration durationOrDefault(Duration value, Duration defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String normalizeLimiterName(String limiterName) {
        if (!StringUtils.hasText(limiterName)) {
            throw new IllegalArgumentException("limiterName must not be blank");
        }
        String normalized = limiterName.trim();
        if (!normalized.matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException("limiterName can only contain letters, digits, _, ., :, and -");
        }
        return normalized;
    }

    private static String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        String normalized = requestId.trim();
        if (!normalized.matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException("requestId can only contain letters, digits, _, ., :, and -");
        }
        return normalized;
    }
}
