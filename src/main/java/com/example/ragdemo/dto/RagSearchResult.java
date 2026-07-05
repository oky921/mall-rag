package com.example.ragdemo.dto;

import java.util.Map;

public class RagSearchResult {

    private final String content;

    private final Map<String, Object> metadata;

    public RagSearchResult(String content, Map<String, Object> metadata) {
        this.content = content;
        this.metadata = metadata;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
