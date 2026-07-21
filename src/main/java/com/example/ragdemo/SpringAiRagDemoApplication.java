package com.example.ragdemo;

import com.example.ragdemo.config.AiProperties;
import com.example.ragdemo.ratelimit.config.RateLimitProperties;
import com.example.ragdemo.config.StoreSearchIndexProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AiProperties.class, RateLimitProperties.class, StoreSearchIndexProperties.class})
public class    SpringAiRagDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiRagDemoApplication.class, args);
    }
}
