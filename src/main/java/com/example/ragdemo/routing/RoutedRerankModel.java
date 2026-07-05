package com.example.ragdemo.routing;

import com.example.ragdemo.rerank.RerankDocument;
import com.example.ragdemo.rerank.RerankModel;
import com.example.ragdemo.rerank.RerankResult;
import java.util.List;

public class RoutedRerankModel implements RerankModel {

    private final List<ModelEndpoint<RerankModel>> endpoints;

    private final ModelRouter modelRouter;

    public RoutedRerankModel(List<ModelEndpoint<RerankModel>> endpoints, ModelRouter modelRouter) {
        this.endpoints = List.copyOf(endpoints);
        this.modelRouter = modelRouter;
    }

    @Override
    public List<RerankResult> rerank(String query, List<RerankDocument> documents, int topK) {
        return modelRouter.execute(ModelCapability.RERANK, endpoints,
                endpoint -> endpoint.getDelegate().rerank(query, documents, topK));
    }
}
