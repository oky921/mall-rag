package com.example.ragdemo.ratelimit.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;

    private String keyPrefix = "ratelimit";

    private String redisAddress = "redis://localhost:6379";

    private String redisPassword;

    private int redisDatabase = 0;

    private String ownerId = "spring-ai-rag-demo";

    private LimitPolicy defaults = new LimitPolicy();

    private Map<String, LimitPolicy> policies = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getRedisAddress() {
        return redisAddress;
    }

    public void setRedisAddress(String redisAddress) {
        this.redisAddress = redisAddress;
    }

    public String getRedisPassword() {
        return redisPassword;
    }

    public void setRedisPassword(String redisPassword) {
        this.redisPassword = redisPassword;
    }

    public int getRedisDatabase() {
        return redisDatabase;
    }

    public void setRedisDatabase(int redisDatabase) {
        this.redisDatabase = redisDatabase;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public LimitPolicy getDefaults() {
        return defaults;
    }

    public void setDefaults(LimitPolicy defaults) {
        this.defaults = defaults;
    }

    public Map<String, LimitPolicy> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, LimitPolicy> policies) {
        this.policies = policies;
    }

    public LimitPolicy policyFor(String limiterName) {
        LimitPolicy override = policies.get(limiterName);
        return override == null ? defaults : defaults.merge(override);
    }

    public static class LimitPolicy {

        private Integer maxConcurrent = 2;

        private Duration waitTimeout = Duration.ofSeconds(30);

        private Duration permitTtl = Duration.ofMinutes(5);

        private Duration requestTtl = Duration.ofMinutes(10);

        private Duration pubSubWait = Duration.ofMillis(500);

        private Duration cleanupInterval = Duration.ofSeconds(10);

        private Integer cleanupBatchSize = 64;

        public Integer getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(Integer maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public Duration getWaitTimeout() {
            return waitTimeout;
        }

        public void setWaitTimeout(Duration waitTimeout) {
            this.waitTimeout = waitTimeout;
        }

        public Duration getPermitTtl() {
            return permitTtl;
        }

        public void setPermitTtl(Duration permitTtl) {
            this.permitTtl = permitTtl;
        }

        public Duration getRequestTtl() {
            return requestTtl;
        }

        public void setRequestTtl(Duration requestTtl) {
            this.requestTtl = requestTtl;
        }

        public Duration getPubSubWait() {
            return pubSubWait;
        }

        public void setPubSubWait(Duration pubSubWait) {
            this.pubSubWait = pubSubWait;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }

        public Integer getCleanupBatchSize() {
            return cleanupBatchSize;
        }

        public void setCleanupBatchSize(Integer cleanupBatchSize) {
            this.cleanupBatchSize = cleanupBatchSize;
        }

        private LimitPolicy merge(LimitPolicy override) {
            LimitPolicy merged = new LimitPolicy();
            merged.maxConcurrent = valueOrDefault(override.maxConcurrent, maxConcurrent);
            merged.waitTimeout = valueOrDefault(override.waitTimeout, waitTimeout);
            merged.permitTtl = valueOrDefault(override.permitTtl, permitTtl);
            merged.requestTtl = valueOrDefault(override.requestTtl, requestTtl);
            merged.pubSubWait = valueOrDefault(override.pubSubWait, pubSubWait);
            merged.cleanupInterval = valueOrDefault(override.cleanupInterval, cleanupInterval);
            merged.cleanupBatchSize = valueOrDefault(override.cleanupBatchSize, cleanupBatchSize);
            return merged;
        }

        private static <T> T valueOrDefault(T value, T defaultValue) {
            return value == null ? defaultValue : value;
        }
    }
}
