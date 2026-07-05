package com.example.ragdemo.routing;

import java.time.Duration;

public class ModelCircuitOpenException extends RuntimeException {

    private final String modelId;

    private final CircuitState state;

    private final Duration retryAfter;

    public ModelCircuitOpenException(String modelId, CircuitState state, Duration retryAfter) {
        super("Circuit is not available for model " + modelId + " in state " + state);
        this.modelId = modelId;
        this.state = state;
        this.retryAfter = retryAfter;
    }

    public String getModelId() {
        return modelId;
    }

    public CircuitState getState() {
        return state;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
