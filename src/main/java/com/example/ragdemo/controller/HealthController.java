package com.example.ragdemo.controller;

import com.example.ragdemo.routing.ModelRouteRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final ObjectProvider<ModelRouteRegistry> modelRouteRegistryProvider;

    public HealthController(ObjectProvider<ModelRouteRegistry> modelRouteRegistryProvider) {
        this.modelRouteRegistryProvider = modelRouteRegistryProvider;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/health/models")
    public Map<String, List<?>> modelHealth() {
        return Map.of("routes", modelRouteRegistryProvider.getObject().snapshots());
    }
}
