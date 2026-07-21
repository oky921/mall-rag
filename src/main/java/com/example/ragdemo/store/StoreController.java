package com.example.ragdemo.store;

import com.example.ragdemo.dto.RagIngestResponse;
import com.example.ragdemo.dto.StoreProductIndexReconciliationResponse;
import com.example.ragdemo.service.StoreProductSearchIndexService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreProductService service;
    private final StoreProductSearchIndexService searchIndexService;

    @Autowired
    public StoreController(StoreProductService service, StoreProductSearchIndexService searchIndexService) {
        this.service = service;
        this.searchIndexService = searchIndexService;
    }

    public StoreController(StoreProductService service) {
        this(service, null);
    }

    @GetMapping("/products")
    public ProductListResponse products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean featured) {
        return service.findProducts(category, keyword, featured);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return service.findCategories();
    }

    @GetMapping("/products/{id}")
    public StoreApiModels.ProductDetailResponse product(@PathVariable Long id) {
        return service.findProduct(id);
    }

    @org.springframework.web.bind.annotation.PostMapping("/search-index/rebuild")
    public RagIngestResponse rebuildSearchIndex() {
        return searchIndexService.rebuild();
    }

    @org.springframework.web.bind.annotation.PostMapping("/search-index/reconcile")
    public StoreProductIndexReconciliationResponse reconcileSearchIndex() {
        return searchIndexService.reconcile();
    }
}
