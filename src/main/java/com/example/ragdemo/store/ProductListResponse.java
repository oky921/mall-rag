package com.example.ragdemo.store;

import java.util.List;

public record ProductListResponse(List<ProductResponse> items, int total) {
}
