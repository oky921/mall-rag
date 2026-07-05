package com.example.ragdemo.dto;

import java.util.List;

public class RagChatResponse {

    private final boolean success;

    private final String content;

    private final List<RagSearchResult> sources;

    private final boolean usedKnowledgeBase;

    private RagChatResponse(boolean success, String content, List<RagSearchResult> sources, boolean usedKnowledgeBase) {
        this.success = success;
        this.content = content;
        this.sources = sources;
        this.usedKnowledgeBase = usedKnowledgeBase;
    }

    public static RagChatResponse ok(String content, List<RagSearchResult> sources, boolean usedKnowledgeBase) {
        return new RagChatResponse(true, content, sources, usedKnowledgeBase);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public List<RagSearchResult> getSources() {
        return sources;
    }

    public boolean isUsedKnowledgeBase() {
        return usedKnowledgeBase;
    }
}
