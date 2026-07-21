package com.example.ragdemo.dto;

import java.math.BigDecimal;

public record ProductSearchResult(
        Long id,
        String code,
        String name,
        String subtitle,
        String category,
        BigDecimal price,
        BigDecimal originalPrice,
        String imageUrl,
        String detailUrl,
        Double score) {
}
