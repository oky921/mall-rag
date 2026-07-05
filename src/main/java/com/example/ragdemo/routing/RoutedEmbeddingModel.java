package com.example.ragdemo.routing;

import java.util.Comparator;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

public class RoutedEmbeddingModel implements EmbeddingModel {

    private final List<ModelEndpoint<EmbeddingModel>> endpoints;

    private final ModelRouter modelRouter;

    public RoutedEmbeddingModel(List<ModelEndpoint<EmbeddingModel>> endpoints, ModelRouter modelRouter) {
        this.endpoints = List.copyOf(endpoints);
        this.modelRouter = modelRouter;
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
        return modelRouter.execute(ModelCapability.EMBEDDING, endpoints, endpoint -> endpoint.getDelegate().call(request));
    }

    @Override
    public float[] embed(Document document) {
        return modelRouter.execute(ModelCapability.EMBEDDING, endpoints, endpoint -> endpoint.getDelegate().embed(document));
    }

    @Override
    public String getEmbeddingContent(Document document) {
        return firstEndpoint().getEmbeddingContent(document);
    }

    @Override
    public int dimensions() {
        return firstEndpoint().dimensions();
    }

    private EmbeddingModel firstEndpoint() {
        return endpoints.stream()
                .filter(ModelEndpoint::isEnabled)
                .sorted(Comparator.comparingInt(ModelEndpoint::getPriority))
                .findFirst()
                .map(ModelEndpoint::getDelegate)
                .orElseThrow(() -> new IllegalStateException("No enabled embedding model configured"));
    }
}
