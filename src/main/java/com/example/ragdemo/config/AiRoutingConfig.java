package com.example.ragdemo.config;

import com.example.ragdemo.dashscope.DashScopeMultiModalChatModel;
import com.example.ragdemo.dashscope.DashScopeTextEmbeddingModel;
import com.example.ragdemo.exception.AiConfigurationException;
import com.example.ragdemo.rerank.PassThroughRerankModel;
import com.example.ragdemo.rerank.RerankModel;
import com.example.ragdemo.routing.ModelCapability;
import com.example.ragdemo.routing.ModelCircuitBreaker;
import com.example.ragdemo.routing.ModelEndpoint;
import com.example.ragdemo.routing.ModelFaultInjector;
import com.example.ragdemo.routing.ModelRouteRegistry;
import com.example.ragdemo.routing.ModelRouter;
import com.example.ragdemo.routing.RoutedChatModel;
import com.example.ragdemo.routing.RoutedEmbeddingModel;
import com.example.ragdemo.routing.RoutedRerankModel;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

@Configuration
public class AiRoutingConfig {

    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个简洁、可靠的 AI 助手。请优先使用中文回答用户问题。";

    @Bean
    public Clock modelRoutingClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ModelRouter modelRouter(ModelFaultInjector faultInjector) {
        return new ModelRouter(faultInjector);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }

    @Bean
    public List<ModelEndpoint<ChatModel>> chatModelEndpoints(AiProperties aiProperties, Clock clock,
            ObservationRegistry observationRegistry) {
        return buildChatEndpoints(aiProperties, clock, observationRegistry);
    }

    @Bean
    public List<ModelEndpoint<EmbeddingModel>> embeddingModelEndpoints(AiProperties aiProperties, Clock clock,
            ObservationRegistry observationRegistry) {
        return buildEmbeddingEndpoints(aiProperties, clock, observationRegistry);
    }

    @Bean
    public List<ModelEndpoint<RerankModel>> rerankModelEndpoints(AiProperties aiProperties, Clock clock) {
        return buildRerankEndpoints(aiProperties, clock);
    }

    @Bean
    @Primary
    public ChatModel chatModel(@Qualifier("chatModelEndpoints") List<ModelEndpoint<ChatModel>> endpoints,
            ModelRouter modelRouter) {
        return new RoutedChatModel(endpoints, modelRouter);
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(@Qualifier("embeddingModelEndpoints") List<ModelEndpoint<EmbeddingModel>> endpoints,
            ModelRouter modelRouter) {
        return new RoutedEmbeddingModel(endpoints, modelRouter);
    }

    @Bean
    public RerankModel rerankModel(@Qualifier("rerankModelEndpoints") List<ModelEndpoint<RerankModel>> endpoints,
            ModelRouter modelRouter) {
        return new RoutedRerankModel(endpoints, modelRouter);
    }

    @Bean
    public ModelRouteRegistry modelRouteRegistry(
            @Qualifier("chatModelEndpoints") List<ModelEndpoint<ChatModel>> chatEndpoints,
            @Qualifier("embeddingModelEndpoints") List<ModelEndpoint<EmbeddingModel>> embeddingEndpoints,
            @Qualifier("rerankModelEndpoints") List<ModelEndpoint<RerankModel>> rerankEndpoints) {
        List<ModelEndpoint<?>> endpoints = new ArrayList<>();
        endpoints.addAll(chatEndpoints);
        endpoints.addAll(embeddingEndpoints);
        endpoints.addAll(rerankEndpoints);
        return new ModelRouteRegistry(endpoints);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .build();
    }

    private List<ModelEndpoint<ChatModel>> buildChatEndpoints(AiProperties aiProperties, Clock clock,
            ObservationRegistry observationRegistry) {
        if (!aiProperties.getRouting().getChat().isEnabled()) {
            return List.of();
        }
        List<AiProperties.ModelEndpointProperties> candidates = aiProperties.getRouting().getChat().getCandidates();
        if (candidates.isEmpty()) {
            candidates = List.of(defaultChatCandidate(aiProperties));
        }
        List<ModelEndpoint<ChatModel>> endpoints = new ArrayList<>();
        for (AiProperties.ModelEndpointProperties candidate : candidates) {
            if (!candidate.isEnabled()) {
                endpoints.add(new ModelEndpoint<>(resolveId(candidate, "chat"), candidate.getProvider(),
                        candidate.getPriority(), false, ModelCapability.CHAT,
                        new ModelCircuitBreaker(resolveId(candidate, "chat"), candidate.getFailureThreshold(),
                                candidate.getOpenDuration(), clock),
                        null));
                continue;
            }
            endpoints.add(new ModelEndpoint<>(resolveId(candidate, "chat"), candidate.getProvider(),
                    candidate.getPriority(), candidate.isEnabled(), ModelCapability.CHAT,
                    new ModelCircuitBreaker(resolveId(candidate, "chat"), candidate.getFailureThreshold(),
                            candidate.getOpenDuration(), clock),
                    buildChatDelegate(candidate, observationRegistry)));
        }
        return endpoints;
    }

    private List<ModelEndpoint<EmbeddingModel>> buildEmbeddingEndpoints(AiProperties aiProperties, Clock clock,
            ObservationRegistry observationRegistry) {
        if (!aiProperties.getRouting().getEmbedding().isEnabled()) {
            return List.of();
        }
        List<AiProperties.ModelEndpointProperties> candidates = aiProperties.getRouting().getEmbedding().getCandidates();
        if (candidates.isEmpty()) {
            candidates = List.of(defaultEmbeddingCandidate(aiProperties));
        }
        List<ModelEndpoint<EmbeddingModel>> endpoints = new ArrayList<>();
        for (AiProperties.ModelEndpointProperties candidate : candidates) {
            if (!candidate.isEnabled()) {
                endpoints.add(new ModelEndpoint<>(resolveId(candidate, "embedding"), candidate.getProvider(),
                        candidate.getPriority(), false, ModelCapability.EMBEDDING,
                        new ModelCircuitBreaker(resolveId(candidate, "embedding"), candidate.getFailureThreshold(),
                                candidate.getOpenDuration(), clock),
                        null));
                continue;
            }
            endpoints.add(new ModelEndpoint<>(resolveId(candidate, "embedding"), candidate.getProvider(),
                    candidate.getPriority(), candidate.isEnabled(), ModelCapability.EMBEDDING,
                    new ModelCircuitBreaker(resolveId(candidate, "embedding"), candidate.getFailureThreshold(),
                            candidate.getOpenDuration(), clock),
                    buildEmbeddingDelegate(candidate, observationRegistry)));
        }
        return endpoints;
    }

    private List<ModelEndpoint<RerankModel>> buildRerankEndpoints(AiProperties aiProperties, Clock clock) {
        if (!aiProperties.getRouting().getRerank().isEnabled()) {
            return List.of(new ModelEndpoint<>("rerank-bypass", "noop", Integer.MAX_VALUE, true, ModelCapability.RERANK,
                    new ModelCircuitBreaker("rerank-bypass", 1, Duration.ZERO, clock),
                    new PassThroughRerankModel()));
        }
        List<AiProperties.ModelEndpointProperties> candidates = aiProperties.getRouting().getRerank().getCandidates();
        if (candidates.isEmpty()) {
            candidates = List.of(defaultRerankCandidate());
        }
        List<ModelEndpoint<RerankModel>> endpoints = new ArrayList<>();
        for (AiProperties.ModelEndpointProperties candidate : candidates) {
            if (!candidate.isEnabled()) {
                endpoints.add(new ModelEndpoint<>(resolveId(candidate, "rerank"), candidate.getProvider(),
                        candidate.getPriority(), false, ModelCapability.RERANK,
                        new ModelCircuitBreaker(resolveId(candidate, "rerank"), candidate.getFailureThreshold(),
                                candidate.getOpenDuration(), clock),
                        null));
                continue;
            }
            endpoints.add(new ModelEndpoint<>(resolveId(candidate, "rerank"), candidate.getProvider(),
                    candidate.getPriority(), candidate.isEnabled(), ModelCapability.RERANK,
                    new ModelCircuitBreaker(resolveId(candidate, "rerank"), candidate.getFailureThreshold(),
                            candidate.getOpenDuration(), clock),
                    buildRerankDelegate(candidate)));
        }
        return endpoints;
    }

    private ChatModel buildChatDelegate(AiProperties.ModelEndpointProperties candidate,
            ObservationRegistry observationRegistry) {
        validateChatCandidate(candidate);
        if (isDashScopeNative(candidate.getProvider())) {
            return new DashScopeMultiModalChatModel(candidate.getApiKey(), candidate.getModel(), candidate.getBaseUrl());
        }
        ensureOpenAiCompatibleProvider(candidate.getProvider(), ModelCapability.CHAT);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(candidate.getBaseUrl())
                .apiKey(candidate.getApiKey())
                .model(candidate.getModel())
                .build();
        return OpenAiChatModel.builder()
                .openAiClient(buildOpenAiClient(candidate))
                .options(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    private EmbeddingModel buildEmbeddingDelegate(AiProperties.ModelEndpointProperties candidate,
            ObservationRegistry observationRegistry) {
        validateEmbeddingCandidate(candidate);
        if (isDashScopeNative(candidate.getProvider())) {
            return new DashScopeTextEmbeddingModel(candidate.getApiKey(), candidate.getModel(),
                    candidate.getDimensions(), candidate.getBaseUrl());
        }
        ensureOpenAiCompatibleProvider(candidate.getProvider(), ModelCapability.EMBEDDING);
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(candidate.getBaseUrl())
                .apiKey(candidate.getApiKey())
                .model(candidate.getModel())
                .dimensions(candidate.getDimensions())
                .build();
        return OpenAiEmbeddingModel.builder()
                .openAiClient(buildOpenAiClient(candidate))
                .options(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    private RerankModel buildRerankDelegate(AiProperties.ModelEndpointProperties candidate) {
        String provider = normalizeProvider(candidate.getProvider());
        if ("noop".equals(provider) || "builtin".equals(provider)) {
            return new PassThroughRerankModel();
        }
        throw new AiConfigurationException("Unsupported rerank provider: " + candidate.getProvider()
                + ". Add an implementation in AiRoutingConfig#buildRerankDelegate.");
    }

    private OpenAIClient buildOpenAiClient(AiProperties.ModelEndpointProperties candidate) {
        ClientOptions options = ClientOptions.builder()
                .httpClient(SpringAiOpenAiHttpClient.builder().build())
                .baseUrl(candidate.getBaseUrl())
                .apiKey(candidate.getApiKey())
                .build();
        return new OpenAIClientImpl(options);
    }

    private void validateChatCandidate(AiProperties.ModelEndpointProperties candidate) {
        if (!StringUtils.hasText(candidate.getModel())) {
            throw new AiConfigurationException("Chat model name is not configured for " + resolveId(candidate, "chat"));
        }
        if (!AiProperties.hasUsableApiKey(candidate.getApiKey())) {
            throw new AiConfigurationException("Chat model api-key is not configured for " + resolveId(candidate, "chat"));
        }
        if (!isDashScopeNative(candidate.getProvider()) && !StringUtils.hasText(candidate.getBaseUrl())) {
            throw new AiConfigurationException("Chat model base-url is not configured for " + resolveId(candidate, "chat"));
        }
    }

    private void validateEmbeddingCandidate(AiProperties.ModelEndpointProperties candidate) {
        if (!StringUtils.hasText(candidate.getModel())) {
            throw new AiConfigurationException("Embedding model name is not configured for " + resolveId(candidate, "embedding"));
        }
        if (candidate.getDimensions() == null || candidate.getDimensions() <= 0) {
            throw new AiConfigurationException("Embedding dimensions must be greater than 0 for "
                    + resolveId(candidate, "embedding"));
        }
        if (!AiProperties.hasUsableApiKey(candidate.getApiKey())) {
            throw new AiConfigurationException("Embedding model api-key is not configured for " + resolveId(candidate, "embedding"));
        }
        if (!isDashScopeNative(candidate.getProvider()) && !StringUtils.hasText(candidate.getBaseUrl())) {
            throw new AiConfigurationException("Embedding model base-url is not configured for "
                    + resolveId(candidate, "embedding"));
        }
    }

    private void ensureOpenAiCompatibleProvider(String provider, ModelCapability capability) {
        String normalized = normalizeProvider(provider);
        if ("openai".equals(normalized) || "openai-compatible".equals(normalized) || "aliyun-bailian".equals(normalized)) {
            return;
        }
        throw new AiConfigurationException("Unsupported " + capability.name().toLowerCase()
                + " provider: " + provider + ". Supported providers: openai-compatible, aliyun-bailian, dashscope-native.");
    }

    private boolean isDashScopeNative(String provider) {
        String normalized = normalizeProvider(provider);
        return "dashscope".equals(normalized) || "dashscope-native".equals(normalized)
                || "aliyun-dashscope".equals(normalized);
    }

    private String normalizeProvider(String provider) {
        return StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "openai-compatible";
    }

    private String resolveId(AiProperties.ModelEndpointProperties candidate, String fallbackPrefix) {
        if (StringUtils.hasText(candidate.getName())) {
            return candidate.getName().trim();
        }
        if (StringUtils.hasText(candidate.getModel())) {
            return fallbackPrefix + ":" + candidate.getModel().trim();
        }
        return fallbackPrefix + ":unnamed";
    }

    private AiProperties.ModelEndpointProperties defaultChatCandidate(AiProperties aiProperties) {
        AiProperties.ModelEndpointProperties candidate = new AiProperties.ModelEndpointProperties();
        candidate.setName("chat-primary");
        candidate.setProvider(aiProperties.getProvider());
        candidate.setBaseUrl(aiProperties.getBaseUrl());
        candidate.setApiKey(aiProperties.getApiKey());
        candidate.setModel(aiProperties.getModel());
        candidate.setPriority(100);
        return candidate;
    }

    private AiProperties.ModelEndpointProperties defaultEmbeddingCandidate(AiProperties aiProperties) {
        AiProperties.ModelEndpointProperties candidate = new AiProperties.ModelEndpointProperties();
        candidate.setName("embedding-primary");
        candidate.setProvider(aiProperties.getProvider());
        candidate.setBaseUrl(aiProperties.getBaseUrl());
        candidate.setApiKey(aiProperties.getApiKey());
        candidate.setModel(aiProperties.getEmbeddingModel());
        candidate.setDimensions(aiProperties.getEmbeddingDimensions());
        candidate.setPriority(100);
        return candidate;
    }

    private AiProperties.ModelEndpointProperties defaultRerankCandidate() {
        AiProperties.ModelEndpointProperties candidate = new AiProperties.ModelEndpointProperties();
        candidate.setName("rerank-pass-through");
        candidate.setProvider("noop");
        candidate.setModel("builtin-pass-through");
        candidate.setPriority(1000);
        return candidate;
    }
}
