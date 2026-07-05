package com.example.ragdemo.config;

import com.example.ragdemo.dashscope.DashScopeMultiModalEmbeddingModel;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(name = "app.image-rag.enabled", havingValue = "true")
public class ImageRagConfig {

    @Bean
    public DashScopeMultiModalEmbeddingModel dashScopeMultiModalEmbeddingModel(
            @Value("${app.image-rag.api-key:${app.ai.api-key}}") String apiKey,
            @Value("${app.image-rag.model:qwen3-vl-embedding}") String model) {
        return new DashScopeMultiModalEmbeddingModel(apiKey, model);
    }

    @Bean(destroyMethod = "close")
    public MilvusClientV2 imageRagMilvusClient(
            @Value("${app.image-rag.milvus.uri:}") String uri,
            @Value("${spring.ai.vectorstore.milvus.client.host:127.0.0.1}") String host,
            @Value("${spring.ai.vectorstore.milvus.client.port:19530}") int port,
            @Value("${app.image-rag.milvus.database:${spring.ai.vectorstore.milvus.database-name:default}}") String database,
            @Value("${app.image-rag.milvus.token:}") String token) {
        String resolvedUri = StringUtils.hasText(uri) ? uri.trim() : "http://" + host + ":" + port;
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(resolvedUri)
                .dbName(database);
        if (token != null && !token.isBlank()) {
            builder.token(token);
        }
        return new MilvusClientV2(builder.build());
    }
}
