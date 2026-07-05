# Redis Distributed Queue Rate Limiter

This project includes a Redis/Redisson based distributed queue rate limiter under
`com.example.ragdemo.ratelimit`.

## Redis Key Design

For a limiter named `chat`, the default key prefix is `ratelimit:{chat}`. The Redis
hash tag keeps all keys in the same Redis Cluster slot so Lua scripts can operate
on them atomically.

| Key | Type | Purpose |
| --- | --- | --- |
| `ratelimit:{chat}:queue` | ZSET | FIFO waiting queue. `member=requestId`, `score=enqueueTimeMillis`. |
| `ratelimit:{chat}:permits` | HASH | Active permits. `field=requestId`, `value=permitExpireAtMillis`. `HLEN` is the active concurrency. |
| `ratelimit:{chat}:request:{requestId}` | HASH | Request lifecycle metadata: status, enqueue time, deadline, owner, acquired time, permit expiry. |
| `ratelimit:{chat}:notify` | Pub/Sub channel | Wake waiting JVMs when queue or permits change. |

## Core Flow

1. `DistributedQueueRateLimiter.acquire` validates the limiter and request id, then writes the request to the ZSET queue.
2. The caller repeatedly invokes a Lua script that performs these steps atomically in Redis:
   clean expired permits, remove timed-out queue heads, check whether the current request is the queue head, check `HLEN(permits) < maxConcurrent`, grant the permit, update request status, and publish a wakeup.
3. If the request cannot enter yet, the JVM waits on a Redisson `RTopic` listener for `ratelimit:{name}:notify`, with a short polling fallback.
4. On timeout, the request is removed from the queue and marked `TIMED_OUT`.
5. Business code should use `try/finally` or `QueueRateLimiter.execute` so `release` always removes the permit and publishes a wakeup.
6. `cancel` removes either a queued request or an acquired permit and wakes other waiters.
7. A scheduled cleanup pass removes expired permits and stale queue heads for active limiters.

## Demo Endpoints

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/api/ratelimit/{name}/acquire` | Queue and acquire a permit. The caller must later release or cancel. |
| `POST` | `/api/ratelimit/{name}/release/{requestId}` | Release an acquired permit. |
| `POST` | `/api/ratelimit/{name}/cancel/{requestId}` | Cancel a queued or acquired request. |
| `GET` | `/api/ratelimit/{name}/status/{requestId}` | Read request status and queue position. |
| `POST` | `/api/ratelimit/{name}/run` | Demo endpoint that acquires, sleeps for `workMillis`, and releases in `finally`. |

## Test Scenarios

The unit tests cover:

| Scenario | Expected Result |
| --- | --- |
| FIFO queue | With `maxConcurrent=1`, `r2` acquires before `r3` after `r1` releases. |
| Max concurrency | With `maxConcurrent=2`, the third request waits until one permit is released. |
| Timeout auto dequeue | A waiting request with a short timeout leaves the queue and throws `RateLimitTimeoutException`. |
| Cancel | A queued request can be canceled and its waiter observes `RateLimitCancelledException`. |
| Release wakeup | Releasing a permit wakes the next queued request quickly. |

## Configuration

Defaults live under `app.ratelimit` in `application.yml`:

```yaml
app:
  ratelimit:
    enabled: true
    redis-address: redis://localhost:6379
    defaults:
      max-concurrent: 2
      wait-timeout: 30s
      permit-ttl: 5m
      request-ttl: 10m
      pub-sub-wait: 500ms
      cleanup-interval: 10s
      cleanup-batch-size: 64
```
