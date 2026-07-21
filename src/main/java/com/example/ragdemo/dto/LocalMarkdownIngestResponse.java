package com.example.ragdemo.dto;

import java.util.Map;

public class LocalMarkdownIngestResponse {

    private final boolean success;

    private final int documents;

    private final int chunks;

    private final Map<String, Integer> chunksByType;

    private LocalMarkdownIngestResponse(boolean success, int documents, int chunks, Map<String, Integer> chunksByType) {
        this.success = success;
        this.documents = documents;
        this.chunks = chunks;
        this.chunksByType = chunksByType;
    }

    public static LocalMarkdownIngestResponse ok(int documents, int chunks, Map<String, Integer> chunksByType) {
        return new LocalMarkdownIngestResponse(true, documents, chunks, chunksByType);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getDocuments() {
        return documents;
    }

    public int getChunks() {
        return chunks;
    }

    public Map<String, Integer> getChunksByType() {
        return chunksByType;
    }
}
