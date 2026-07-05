package com.example.ragdemo.dto;

import java.util.List;

public class RagSearchResponse {

    private final boolean success;

    private final List<RagSearchResult> results;

    private RagSearchResponse(boolean success, List<RagSearchResult> results) {
        this.success = success;
        this.results = results;
    }

    public static RagSearchResponse ok(List<RagSearchResult> results) {
        return new RagSearchResponse(true, results);
    }

    public boolean isSuccess() {
        return success;
    }

    public List<RagSearchResult> getResults() {
        return results;
    }
}
