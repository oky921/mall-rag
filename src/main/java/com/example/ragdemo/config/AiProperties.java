package com.example.ragdemo.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String provider;

    private String baseUrl;

    private String apiKey;

    private String model;

    private String embeddingModel;

    private Integer embeddingDimensions;

    private RoutingProperties routing = new RoutingProperties();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(Integer embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public RoutingProperties getRouting() {
        return routing;
    }

    public void setRouting(RoutingProperties routing) {
        this.routing = routing;
    }

    public boolean hasUsableApiKey() {
        return hasUsableApiKey(apiKey);
    }

    public static boolean hasUsableApiKey(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return !normalized.startsWith("todo-")
                && !normalized.startsWith("replace-")
                && !normalized.contains("你的")
                && !normalized.toLowerCase().contains("api key")
                && normalized.chars().allMatch(ch -> ch >= 33 && ch <= 126);
    }

    public static class RoutingProperties {

        private CapabilityProperties chat = new CapabilityProperties();

        private CapabilityProperties embedding = new CapabilityProperties();

        private CapabilityProperties rerank = new CapabilityProperties();

        public CapabilityProperties getChat() {
            return chat;
        }

        public void setChat(CapabilityProperties chat) {
            this.chat = chat;
        }

        public CapabilityProperties getEmbedding() {
            return embedding;
        }

        public void setEmbedding(CapabilityProperties embedding) {
            this.embedding = embedding;
        }

        public CapabilityProperties getRerank() {
            return rerank;
        }

        public void setRerank(CapabilityProperties rerank) {
            this.rerank = rerank;
        }
    }

    public static class CapabilityProperties {

        private boolean enabled = true;

        private List<ModelEndpointProperties> candidates = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<ModelEndpointProperties> getCandidates() {
            return candidates;
        }

        public void setCandidates(List<ModelEndpointProperties> candidates) {
            this.candidates = candidates;
        }
    }

    public static class ModelEndpointProperties {

        private boolean enabled = true;

        private String name;

        private String provider;

        private String baseUrl;

        private String apiKey;

        private String model;

        private Integer dimensions;

        private int priority = 100;

        private int failureThreshold = 3;

        private Duration openDuration = Duration.ofSeconds(30);

        private Map<String, String> metadata = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getDimensions() {
            return dimensions;
        }

        public void setDimensions(Integer dimensions) {
            this.dimensions = dimensions;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public Duration getOpenDuration() {
            return openDuration;
        }

        public void setOpenDuration(Duration openDuration) {
            this.openDuration = openDuration;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
