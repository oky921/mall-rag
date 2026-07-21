package com.example.ragdemo.dto;

import java.util.List;

public class MallChatResponse {

    private final boolean success;

    private final String content;

    private final List<MallImageSearchResult> imageResults;

    private final List<ProductSearchResult> productResults;

    private final List<RagSearchResult> sources;

    private final boolean usedImageSearch;

    private final boolean usedKnowledgeBase;

    private MallChatResponse(boolean success, String content, List<MallImageSearchResult> imageResults,
            List<ProductSearchResult> productResults, List<RagSearchResult> sources,
            boolean usedImageSearch, boolean usedKnowledgeBase) {
        this.success = success;
        this.content = content;
        this.imageResults = imageResults;
        this.productResults = productResults;
        this.sources = sources;
        this.usedImageSearch = usedImageSearch;
        this.usedKnowledgeBase = usedKnowledgeBase;
    }

    public static MallChatResponse imageSearch(String content, List<MallImageSearchResult> imageResults) {
        return new MallChatResponse(true, content, imageResults, List.of(), List.of(), true, false);
    }

    public static MallChatResponse productSearch(String content, List<ProductSearchResult> productResults) {
        return new MallChatResponse(true, content, List.of(), productResults, List.of(), false, false);
    }

    public static MallChatResponse rag(String content, List<RagSearchResult> sources, boolean usedKnowledgeBase) {
        return new MallChatResponse(true, content, List.of(), List.of(), sources, false, usedKnowledgeBase);
    }

    public static MallChatResponse plain(String content) {
        return new MallChatResponse(true, content, List.of(), List.of(), List.of(), false, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public List<MallImageSearchResult> getImageResults() {
        return imageResults;
    }

    public List<MallImageSearchResult> getImage_results() {
        return imageResults;
    }

    public List<ProductSearchResult> getProductResults() {
        return productResults;
    }

    public List<RagSearchResult> getSources() {
        return sources;
    }

    public boolean isUsedImageSearch() {
        return usedImageSearch;
    }

    public boolean isUsed_image_search() {
        return usedImageSearch;
    }

    public boolean isUsedKnowledgeBase() {
        return usedKnowledgeBase;
    }
}
