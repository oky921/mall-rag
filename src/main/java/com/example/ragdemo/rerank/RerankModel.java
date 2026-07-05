package com.example.ragdemo.rerank;

import java.util.List;

public interface RerankModel {

    List<RerankResult> rerank(String query, List<RerankDocument> documents, int topK);
}
