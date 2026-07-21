package com.example.ragdemo.store;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String code,
        String name,
        String subtitle,
        String description,
        String category,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer stock,
        Integer sales,
        BigDecimal rating,
        String imageUrl,
        Boolean featured) {

    static ProductResponse from(StoreProduct product) {
        return new ProductResponse(
                product.getId(), product.getCode(), product.getName(), product.getSubtitle(),
                product.getDescription(), product.getCategory(), product.getPrice(),
                product.getOriginalPrice(), product.getStock(), product.getSales(), product.getRating(),
                product.getImageUrl(), product.getFeatured());
    }
}
