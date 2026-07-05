package com.example.ragdemo.ratelimit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ragdemo.ratelimit.config.RateLimitProperties;
import com.example.ragdemo.ratelimit.exception.RateLimitCancelledException;
import com.example.ragdemo.ratelimit.exception.RateLimitTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DistributedQueueRateLimiterTest {

    @Test
    void shouldAcquireInFifoOrder() throws Exception {
        TestLimiter testLimiter = limiterWithMaxConcurrent(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (RateLimitPermit ignored = testLimiter.limiter.acquire("fifo", "r1", Duration.ofMillis(500))) {
            Future<RateLimitPermit> r2 = executor.submit(
                    () -> testLimiter.limiter.acquire("fifo", "r2", Duration.ofSeconds(1)));
            awaitStatus(testLimiter.repository, "r2", RateLimitStatus.QUEUED);

            Future<RateLimitPermit> r3 = executor.submit(
                    () -> testLimiter.limiter.acquire("fifo", "r3", Duration.ofSeconds(1)));
            awaitStatus(testLimiter.repository, "r3", RateLimitStatus.QUEUED);

            assertFalse(r2.isDone());
            assertFalse(r3.isDone());

            ignored.close();
            RateLimitPermit second = r2.get(500, TimeUnit.MILLISECONDS);
            assertEquals("r2", second.requestId());
            assertFalse(r3.isDone());

            second.close();
            RateLimitPermit third = r3.get(500, TimeUnit.MILLISECONDS);
            assertEquals("r3", third.requestId());
            third.close();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRespectMaxConcurrentPermits() throws Exception {
        TestLimiter testLimiter = limiterWithMaxConcurrent(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RateLimitPermit r1 = testLimiter.limiter.acquire("concurrency", "r1", Duration.ofMillis(500));
        RateLimitPermit r2 = testLimiter.limiter.acquire("concurrency", "r2", Duration.ofMillis(500));
        try {
            Future<RateLimitPermit> r3 = executor.submit(
                    () -> testLimiter.limiter.acquire("concurrency", "r3", Duration.ofSeconds(1)));
            awaitStatus(testLimiter.repository, "r3", RateLimitStatus.QUEUED);
            assertFalse(r3.isDone());

            r1.close();
            RateLimitPermit third = r3.get(500, TimeUnit.MILLISECONDS);
            assertEquals("r3", third.requestId());
            third.close();
        } finally {
            r1.close();
            r2.close();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldTimeoutAndLeaveQueue() {
        TestLimiter testLimiter = limiterWithMaxConcurrent(1);
        try (RateLimitPermit ignored = testLimiter.limiter.acquire("timeout", "r1", Duration.ofMillis(500))) {
            assertThrows(RateLimitTimeoutException.class,
                    () -> testLimiter.limiter.acquire("timeout", "r2", Duration.ofMillis(40)));
            assertEquals(RateLimitStatus.TIMED_OUT, testLimiter.repository.statusFor("r2"));
            assertEquals(-1, testLimiter.repository.positionOf("r2"));
        }
    }

    @Test
    void shouldCancelQueuedRequest() throws Exception {
        TestLimiter testLimiter = limiterWithMaxConcurrent(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (RateLimitPermit ignored = testLimiter.limiter.acquire("cancel", "r1", Duration.ofMillis(500))) {
            Future<RateLimitPermit> r2 = executor.submit(
                    () -> testLimiter.limiter.acquire("cancel", "r2", Duration.ofSeconds(1)));
            awaitStatus(testLimiter.repository, "r2", RateLimitStatus.QUEUED);

            testLimiter.limiter.cancel("cancel", "r2");

            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> r2.get(500, TimeUnit.MILLISECONDS));
            assertInstanceOf(RateLimitCancelledException.class, ex.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldWakeNextRequestWhenPermitIsReleased() throws Exception {
        TestLimiter testLimiter = limiterWithMaxConcurrent(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RateLimitPermit first = testLimiter.limiter.acquire("release", "r1", Duration.ofMillis(500));
        try {
            Future<RateLimitPermit> secondFuture = executor.submit(
                    () -> testLimiter.limiter.acquire("release", "r2", Duration.ofSeconds(1)));
            awaitStatus(testLimiter.repository, "r2", RateLimitStatus.QUEUED);

            first.close();

            RateLimitPermit second = secondFuture.get(500, TimeUnit.MILLISECONDS);
            assertEquals("r2", second.requestId());
            second.close();
        } finally {
            first.close();
            executor.shutdownNow();
        }
    }

    private static TestLimiter limiterWithMaxConcurrent(int maxConcurrent) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getDefaults().setMaxConcurrent(maxConcurrent);
        properties.getDefaults().setWaitTimeout(Duration.ofSeconds(1));
        properties.getDefaults().setPermitTtl(Duration.ofSeconds(10));
        properties.getDefaults().setRequestTtl(Duration.ofSeconds(10));
        properties.getDefaults().setPubSubWait(Duration.ofMillis(20));
        TestSignalBus signalBus = new TestSignalBus();
        FakeRateLimitRepository repository = new FakeRateLimitRepository(signalBus);
        DistributedQueueRateLimiter limiter = new DistributedQueueRateLimiter(
                properties, repository, signalBus, Clock.systemUTC());
        return new TestLimiter(limiter, repository);
    }

    private static void awaitStatus(FakeRateLimitRepository repository, String requestId, RateLimitStatus status)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < deadline) {
            if (repository.statusFor(requestId) == status) {
                return;
            }
            Thread.sleep(5);
        }
        assertEquals(status, repository.statusFor(requestId));
    }

    private record TestLimiter(DistributedQueueRateLimiter limiter, FakeRateLimitRepository repository) {
    }

    private static class TestSignalBus implements RateLimitSignalBus {

        private final ConcurrentMap<String, List<CountDownLatch>> waiters = new ConcurrentHashMap<>();

        @Override
        public void await(String channel, Duration timeout) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            List<CountDownLatch> channelWaiters = waiters.computeIfAbsent(channel,
                    ignored -> new CopyOnWriteArrayList<>());
            channelWaiters.add(latch);
            try {
                latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                channelWaiters.remove(latch);
            }
        }

        void signal(String channel) {
            List<CountDownLatch> channelWaiters = waiters.get(channel);
            if (channelWaiters == null) {
                return;
            }
            for (CountDownLatch waiter : channelWaiters) {
                waiter.countDown();
            }
        }
    }

    private static class FakeRateLimitRepository implements RateLimitRepository {

        private final TestSignalBus signalBus;

        private final Deque<String> queue = new ArrayDeque<>();

        private final Map<String, RateLimitStatus> statuses = new HashMap<>();

        private final Map<String, Long> deadlines = new HashMap<>();

        private final Map<String, Long> permits = new HashMap<>();

        private String channel;

        FakeRateLimitRepository(TestSignalBus signalBus) {
            this.signalBus = signalBus;
        }

        @Override
        public synchronized void enqueue(RateLimitKeys keys, String requestId, long nowMillis, long deadlineMillis,
                long requestTtlMillis, String ownerId) {
            this.channel = keys.notifyChannel();
            if (!statuses.containsKey(requestId)) {
                queue.addLast(requestId);
                statuses.put(requestId, RateLimitStatus.QUEUED);
                deadlines.put(requestId, deadlineMillis);
            }
            signalBus.signal(keys.notifyChannel());
        }

        @Override
        public synchronized TryAcquireResult tryAcquire(RateLimitKeys keys, String requestId, long nowMillis,
                int maxConcurrent, long permitExpireAtMillis, long requestTtlMillis, int cleanupBatchSize) {
            cleanup(keys, nowMillis, requestTtlMillis, cleanupBatchSize);
            RateLimitStatus status = statuses.getOrDefault(requestId, RateLimitStatus.MISSING);
            if (status != RateLimitStatus.QUEUED) {
                return new TryAcquireResult(status, -1, permits.size());
            }
            if (deadlines.getOrDefault(requestId, 0L) <= nowMillis) {
                queue.remove(requestId);
                statuses.put(requestId, RateLimitStatus.TIMED_OUT);
                signalBus.signal(keys.notifyChannel());
                return new TryAcquireResult(RateLimitStatus.TIMED_OUT, -1, permits.size());
            }
            if (!requestId.equals(queue.peekFirst())) {
                return new TryAcquireResult(RateLimitStatus.QUEUED, positionOf(requestId), permits.size());
            }
            if (permits.size() >= maxConcurrent) {
                return new TryAcquireResult(RateLimitStatus.QUEUED, 1, permits.size());
            }
            queue.removeFirst();
            permits.put(requestId, permitExpireAtMillis);
            statuses.put(requestId, RateLimitStatus.ACQUIRED);
            signalBus.signal(keys.notifyChannel());
            return new TryAcquireResult(RateLimitStatus.ACQUIRED, 0, permits.size());
        }

        @Override
        public synchronized ReleaseResult release(RateLimitKeys keys, String requestId, long nowMillis,
                long requestTtlMillis, RateLimitStatus finalStatus) {
            boolean permitReleased = permits.remove(requestId) != null;
            boolean queueRemoved = queue.remove(requestId);
            if (statuses.containsKey(requestId)) {
                statuses.put(requestId, finalStatus);
            }
            signalBus.signal(keys.notifyChannel());
            return new ReleaseResult(finalStatus, permitReleased, queueRemoved);
        }

        @Override
        public synchronized RateLimitStatusSnapshot status(RateLimitKeys keys, String requestId) {
            return new RateLimitStatusSnapshot(keys.limiterName(), requestId, statusFor(requestId),
                    positionOf(requestId), permits.size());
        }

        @Override
        public synchronized int cleanup(RateLimitKeys keys, long nowMillis, long requestTtlMillis,
                int cleanupBatchSize) {
            int cleaned = 0;
            List<String> expiredPermits = new ArrayList<>();
            permits.forEach((requestId, expiresAt) -> {
                if (expiresAt <= nowMillis) {
                    expiredPermits.add(requestId);
                }
            });
            for (String requestId : expiredPermits) {
                permits.remove(requestId);
                cleaned++;
            }
            while (!queue.isEmpty() && deadlines.getOrDefault(queue.peekFirst(), 0L) <= nowMillis) {
                String requestId = queue.removeFirst();
                statuses.put(requestId, RateLimitStatus.TIMED_OUT);
                cleaned++;
            }
            if (cleaned > 0 && channel != null) {
                signalBus.signal(channel);
            }
            return cleaned;
        }

        synchronized RateLimitStatus statusFor(String requestId) {
            return statuses.getOrDefault(requestId, RateLimitStatus.MISSING);
        }

        synchronized long positionOf(String requestId) {
            int index = 1;
            for (String queuedRequestId : queue) {
                if (queuedRequestId.equals(requestId)) {
                    return index;
                }
                index++;
            }
            return -1;
        }
    }
}
