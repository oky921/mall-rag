package com.example.ragdemo.routing;

public class ModelEndpoint<T> {

    private final String id;

    private final String provider;

    private final int priority;

    private final boolean enabled;

    private final ModelCapability capability;

    private final ModelCircuitBreaker circuitBreaker;

    private final T delegate;

    public ModelEndpoint(String id, String provider, int priority, boolean enabled, ModelCapability capability,
            ModelCircuitBreaker circuitBreaker, T delegate) {
        this.id = id;
        this.provider = provider;
        this.priority = priority;
        this.enabled = enabled;
        this.capability = capability;
        this.circuitBreaker = circuitBreaker;
        this.delegate = delegate;
    }

    public String getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ModelCapability getCapability() {
        return capability;
    }

    public ModelCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public T getDelegate() {
        return delegate;
    }
}
