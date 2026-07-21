package com.example.ragdemo.dto;

public record StoreProductIndexReconciliationResponse(
        int mysqlProducts,
        int indexedProducts,
        int added,
        int updated,
        int deleted) {
}
