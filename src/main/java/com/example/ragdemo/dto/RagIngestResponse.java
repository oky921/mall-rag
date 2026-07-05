package com.example.ragdemo.dto;

public class RagIngestResponse {

    private final boolean success;

    private final int documents;

    private RagIngestResponse(boolean success, int documents) {
        this.success = success;
        this.documents = documents;
    }

    public static RagIngestResponse ok(int documents) {
        return new RagIngestResponse(true, documents);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getDocuments() {
        return documents;
    }
}
