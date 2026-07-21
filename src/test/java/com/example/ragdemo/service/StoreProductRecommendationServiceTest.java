package com.example.ragdemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.ragdemo.dto.ProductSearchResult;
import com.example.ragdemo.dto.RagSearchResponse;
import com.example.ragdemo.dto.RagSearchResult;
import com.example.ragdemo.store.StoreProduct;
import com.example.ragdemo.store.StoreProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StoreProductRecommendationServiceTest {

    private StoreProductRepository repository;
    private RagService ragService;
    private StoreProductRecommendationService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repository = mock(StoreProductRepository.class);
        ragService = mock(RagService.class);
        ObjectProvider<RagService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ragService);
        service = new StoreProductRecommendationService(repository, provider);
    }

    @Test
    void hydratesSemanticCandidateFromMysqlAndBuildsDetailUrl() {
        StoreProduct headphones = product(2L, "DIG-1002", "静界降噪耳机", "沉浸声场 · 40 小时续航",
                "舒适包耳设计，适合通勤和工作。", "899");
        RagSearchResult candidate = new RagSearchResult("vector snapshot",
                new java.util.HashMap<>(Map.of("type", "product", "product_id", "DIG-1002", "rerankScore", 0.91)));
        when(ragService.search(any())).thenReturn(RagSearchResponse.ok(List.of(candidate)));
        when(repository.findByCodeIn(any())).thenReturn(List.of(headphones));
        when(repository.findAll()).thenReturn(List.of(headphones));

        List<ProductSearchResult> results = service.recommend("推荐一款1000元以内适合通勤的降噪耳机", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).code()).isEqualTo("DIG-1002");
        assertThat(results.get(0).price()).isEqualByComparingTo("899");
        assertThat(results.get(0).detailUrl()).isEqualTo("/mall/products/2");
    }

    @Test
    void appliesBudgetAgainstCurrentMysqlPrice() {
        StoreProduct headphones = product(2L, "DIG-1002", "静界降噪耳机", "降噪", "适合通勤", "899");
        when(ragService.search(any())).thenThrow(new IllegalStateException("Milvus unavailable"));
        when(repository.findAll()).thenReturn(List.of(headphones));

        assertThat(service.recommend("想买500元以内的降噪耳机", 5)).isEmpty();
    }

    @Test
    void fallsBackToMysqlWhenVectorSearchIsUnavailable() {
        StoreProduct headphones = product(2L, "DIG-1002", "静界降噪耳机", "降噪", "适合通勤", "899");
        when(ragService.search(any())).thenThrow(new IllegalStateException("Milvus unavailable"));
        when(repository.findAll()).thenReturn(List.of(headphones));

        assertThat(service.recommend("推荐1000元以内的通勤降噪耳机", 5))
                .extracting(ProductSearchResult::code)
                .containsExactly("DIG-1002");
    }

    @Test
    void recognizesScenarioBasedShoppingIntent() {
        assertThat(service.isProductShoppingIntent("想买一款500元以内适合通勤的降噪耳机")).isTrue();
        assertThat(service.isProductShoppingIntent("解释一下什么是向量数据库")).isFalse();
    }

    private StoreProduct product(Long id, String code, String name, String subtitle, String description, String price) {
        StoreProduct product = mock(StoreProduct.class);
        lenient().when(product.getId()).thenReturn(id);
        lenient().when(product.getCode()).thenReturn(code);
        lenient().when(product.getName()).thenReturn(name);
        lenient().when(product.getSubtitle()).thenReturn(subtitle);
        lenient().when(product.getDescription()).thenReturn(description);
        lenient().when(product.getCategory()).thenReturn("数码家电");
        lenient().when(product.getPrice()).thenReturn(new BigDecimal(price));
        lenient().when(product.getOriginalPrice()).thenReturn(new BigDecimal("1099"));
        lenient().when(product.getImageUrl()).thenReturn("https://example.com/headphones.jpg");
        lenient().when(product.getFeatured()).thenReturn(true);
        lenient().when(product.getActive()).thenReturn(true);
        lenient().when(product.getSales()).thenReturn(100);
        return product;
    }
}
