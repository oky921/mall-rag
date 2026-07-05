package com.example.ragdemo.controller;

import com.example.ragdemo.routing.ModelFaultInjector;
import com.example.ragdemo.routing.ModelRouteEvent;
import com.example.ragdemo.routing.ModelRouteRegistry;
import com.example.ragdemo.routing.ModelRouteTraceContext;
import com.example.ragdemo.routing.RouteStatusSnapshot;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/models")
public class ModelDebugController {

    private final ChatClient chatClient;

    private final EmbeddingModel embeddingModel;

    private final ModelRouteRegistry modelRouteRegistry;

    private final ModelFaultInjector faultInjector;

    public ModelDebugController(ChatClient chatClient, EmbeddingModel embeddingModel,
            ModelRouteRegistry modelRouteRegistry, ModelFaultInjector faultInjector) {
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
        this.modelRouteRegistry = modelRouteRegistry;
        this.faultInjector = faultInjector;
    }

    @GetMapping("/routes")
    public DebugRoutesResponse routes() {
        return new DebugRoutesResponse(modelRouteRegistry.snapshots(), faultInjector.snapshot());
    }

    @PostMapping("/faults")
    public DebugRoutesResponse injectFault(@RequestBody FaultRequest request) {
        if (!StringUtils.hasText(request.endpointId())) {
            throw new IllegalArgumentException("endpointId is required");
        }
        int failures = request.failures() == null ? 1 : request.failures();
        faultInjector.failNext(request.endpointId().trim(), failures);
        return routes();
    }

    @DeleteMapping("/faults")
    public DebugRoutesResponse clearFaults() {
        faultInjector.clear();
        return routes();
    }

    @PostMapping("/chat")
    public DebugChatResponse chat(@RequestBody DebugTextRequest request) {
        String message = normalizeMessage(request);
        List<RouteStatusSnapshot> before = modelRouteRegistry.snapshots();
        ModelRouteTraceContext.start();
        try {
            String content = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            return new DebugChatResponse(message, content, null, ModelRouteTraceContext.snapshot(), before,
                    modelRouteRegistry.snapshots());
        } catch (RuntimeException ex) {
            return new DebugChatResponse(message, null, ex.getMessage(), ModelRouteTraceContext.snapshot(), before,
                    modelRouteRegistry.snapshots());
        } finally {
            ModelRouteTraceContext.clear();
        }
    }

    @PostMapping("/embedding")
    public DebugEmbeddingResponse embedding(@RequestBody DebugTextRequest request) {
        String message = normalizeMessage(request);
        List<RouteStatusSnapshot> before = modelRouteRegistry.snapshots();
        ModelRouteTraceContext.start();
        try {
            float[] vector = embeddingModel.embed(new Document(message));
            return new DebugEmbeddingResponse(message, vector.length, preview(vector), null,
                    ModelRouteTraceContext.snapshot(), before, modelRouteRegistry.snapshots());
        } catch (RuntimeException ex) {
            return new DebugEmbeddingResponse(message, 0, List.of(), ex.getMessage(),
                    ModelRouteTraceContext.snapshot(), before, modelRouteRegistry.snapshots());
        } finally {
            ModelRouteTraceContext.clear();
        }
    }

    private String normalizeMessage(DebugTextRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("message is required");
        }
        return request.message().trim();
    }

    private List<Float> preview(float[] vector) {
        int limit = Math.min(8, vector.length);
        Float[] values = new Float[limit];
        for (int i = 0; i < limit; i++) {
            values[i] = vector[i];
        }
        return Arrays.asList(values);
    }

    public record DebugTextRequest(String message) {
    }

    public record FaultRequest(String endpointId, Integer failures) {
    }

    public record DebugRoutesResponse(List<RouteStatusSnapshot> routes, Map<String, Integer> injectedFaults) {
    }

    public record DebugChatResponse(
            String input,
            String output,
            String error,
            List<ModelRouteEvent> events,
            List<RouteStatusSnapshot> before,
            List<RouteStatusSnapshot> after) {
    }

    public record DebugEmbeddingResponse(
            String input,
            int dimensions,
            List<Float> vectorPreview,
            String error,
            List<ModelRouteEvent> events,
            List<RouteStatusSnapshot> before,
            List<RouteStatusSnapshot> after) {
    }
}
