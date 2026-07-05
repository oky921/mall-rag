package com.example.ragdemo.rerank;

import java.util.ArrayList;
import java.util.List;

public class PassThroughRerankModel implements RerankModel {

    @Override
    public List<RerankResult> rerank(String query, List<RerankDocument> documents, int topK) {
        List<RerankResult> results = new ArrayList<>();
        int limit = Math.min(topK, documents.size());
        for (int i = 0; i < limit; i++) {
            results.add(new RerankResult(documents.get(i), limit - i, i));
        }
        return results;
    }
}
