package com.example.ragdemo.routing;

import java.util.ArrayList;
import java.util.List;

public final class ModelRouteTraceContext {

    private static final ThreadLocal<List<ModelRouteEvent>> EVENTS = new ThreadLocal<>();

    private ModelRouteTraceContext() {
    }

    public static void start() {
        EVENTS.set(new ArrayList<>());
    }

    public static void record(ModelRouteEvent event) {
        List<ModelRouteEvent> events = EVENTS.get();
        if (events != null) {
            events.add(event);
        }
    }

    public static List<ModelRouteEvent> snapshot() {
        List<ModelRouteEvent> events = EVENTS.get();
        return events == null ? List.of() : List.copyOf(events);
    }

    public static void clear() {
        EVENTS.remove();
    }
}
