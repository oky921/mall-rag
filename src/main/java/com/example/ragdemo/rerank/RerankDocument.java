package com.example.ragdemo.rerank;

import java.util.Map;

public record RerankDocument(String content, Map<String, Object> metadata) {
}
