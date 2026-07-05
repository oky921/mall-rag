package com.example.ragdemo.routing;

public record ModelRouteEvent(
        ModelCapability capability,
        String endpointId,
        String provider,
        String action,
        CircuitState state,
        String message) {
}
