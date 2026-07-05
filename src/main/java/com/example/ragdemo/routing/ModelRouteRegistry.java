package com.example.ragdemo.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModelRouteRegistry {

    private final List<ModelEndpoint<?>> endpoints;

    public ModelRouteRegistry(List<ModelEndpoint<?>> endpoints) {
        this.endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }

    public List<RouteStatusSnapshot> snapshots() {
        List<RouteStatusSnapshot> snapshots = new ArrayList<>();
        for (ModelEndpoint<?> endpoint : endpoints) {
            ModelCircuitBreaker.Snapshot breakerSnapshot = endpoint.getCircuitBreaker().snapshot();
            snapshots.add(new RouteStatusSnapshot(
                    endpoint.getId(),
                    endpoint.getProvider(),
                    endpoint.getCapability(),
                    endpoint.getPriority(),
                    endpoint.isEnabled(),
                    breakerSnapshot.state(),
                    breakerSnapshot.consecutiveFailures(),
                    breakerSnapshot.remainingOpenDuration()));
        }
        return Collections.unmodifiableList(snapshots);
    }
}
