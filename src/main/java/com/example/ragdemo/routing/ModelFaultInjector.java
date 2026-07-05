package com.example.ragdemo.routing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ModelFaultInjector {

    private final Map<String, AtomicInteger> failuresByEndpoint = new ConcurrentHashMap<>();

    public void failNext(String endpointId, int count) {
        if (count <= 0) {
            failuresByEndpoint.remove(endpointId);
            return;
        }
        failuresByEndpoint.put(endpointId, new AtomicInteger(count));
    }

    public boolean shouldFail(String endpointId) {
        AtomicInteger remaining = failuresByEndpoint.get(endpointId);
        if (remaining == null) {
            return false;
        }
        int left = remaining.getAndUpdate(value -> Math.max(0, value - 1));
        if (left <= 1) {
            failuresByEndpoint.remove(endpointId);
        }
        return left > 0;
    }

    public void clear() {
        failuresByEndpoint.clear();
    }

    public Map<String, Integer> snapshot() {
        Map<String, Integer> snapshot = new java.util.LinkedHashMap<>();
        failuresByEndpoint.forEach((endpointId, count) -> snapshot.put(endpointId, count.get()));
        return snapshot;
    }
}
