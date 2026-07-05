package com.example.ragdemo.ratelimit.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "app.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitRedisConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(RateLimitProperties properties) {
        Config config = new Config();
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress(properties.getRedisAddress())
                .setDatabase(properties.getRedisDatabase());
        if (StringUtils.hasText(properties.getRedisPassword())) {
            singleServer.setPassword(properties.getRedisPassword());
        }
        return Redisson.create(config);
    }
}
