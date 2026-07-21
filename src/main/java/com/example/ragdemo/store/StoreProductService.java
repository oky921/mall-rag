package com.example.ragdemo.store;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class StoreProductService {

    private final StoreProductRepository repository;
    private final StoreSkuRepository skuRepository;

    public StoreProductService(StoreProductRepository repository, StoreSkuRepository skuRepository) {
        this.repository = repository;
        this.skuRepository = skuRepository;
    }

    public ProductListResponse findProducts(String category, String keyword, Boolean featured) {
        String normalizedCategory = normalize(category);
        String normalizedKeyword = normalize(keyword);
        List<ProductResponse> products = repository.search(normalizedCategory, normalizedKeyword, featured)
                .stream()
                .map(ProductResponse::from)
                .toList();
        return new ProductListResponse(products, products.size());
    }

    public List<CategoryResponse> findCategories() {
        return repository.countByCategory().stream()
                .map(row -> new CategoryResponse((String) row[0], (Long) row[1]))
                .toList();
    }

    public StoreApiModels.ProductDetailResponse findProduct(Long id) {
        StoreProduct product = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        List<StoreApiModels.SkuResponse> skus = skuRepository
                .findByProductIdAndEnabledTrueOrderById(id).stream()
                .map(StoreApiModels.SkuResponse::from)
                .toList();
        return new StoreApiModels.ProductDetailResponse(ProductResponse.from(product), skus);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
