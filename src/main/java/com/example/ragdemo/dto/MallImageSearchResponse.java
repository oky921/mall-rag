package com.example.ragdemo.dto;

import java.util.List;

public class MallImageSearchResponse {

    private final boolean success;

    private final List<MallImageSearchResult> results;

    private MallImageSearchResponse(boolean success, List<MallImageSearchResult> results) {
        this.success = success;
        this.results = results;
    }

    public static MallImageSearchResponse ok(List<MallImageSearchResult> results) {
        return new MallImageSearchResponse(true, results);
    }

    public boolean isSuccess() {
        return success;
    }

    public List<MallImageSearchResult> getResults() {
        return results;
    }
}
