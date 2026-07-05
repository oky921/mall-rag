package com.example.ragdemo.rerank;

public record RerankResult(RerankDocument document, double score, int originalIndex) {
}
