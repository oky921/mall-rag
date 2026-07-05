package com.example.ragdemo.dto;

import java.util.Map;

public class MallImageSearchResult {

    private final String id;

    private final String productId;

    private final String skuId;

    private final String title;

    private final String imageUrl;

    private final String link;

    private final Float score;

    private final Map<String, Object> metadata;

    public MallImageSearchResult(String id, String productId, String skuId, String title, String imageUrl,
            String link, Float score, Map<String, Object> metadata) {
        this.id = id;
        this.productId = productId;
        this.skuId = skuId;
        this.title = title;
        this.imageUrl = imageUrl;
        this.link = link;
        this.score = score;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getProduct_id() {
        return productId;
    }

    public String getSkuId() {
        return skuId;
    }

    public String getSku_id() {
        return skuId;
    }

    public String getTitle() {
        return title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getImage_url() {
        return imageUrl;
    }

    public String getLink() {
        return link;
    }

    public Float getScore() {
        return score;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
