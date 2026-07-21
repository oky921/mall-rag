package com.example.ragdemo.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreProductServiceTest {

    @Mock
    private StoreProductRepository repository;

    @InjectMocks
    private StoreProductService service;

    @Test
    void findsProductsWithNormalizedFilters() {
        StoreProduct product = new StoreProduct(
                "DIG-1", "测试耳机", "无线降噪", "适合通勤", "数码家电",
                new BigDecimal("299"), new BigDecimal("399"), 10, 20,
                new BigDecimal("4.8"), "https://example.com/product.jpg", true);
        when(repository.search("数码家电", "耳机", true)).thenReturn(List.of(product));

        ProductListResponse response = service.findProducts("  数码家电 ", " 耳机  ", true);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("测试耳机");
            assertThat(item.price()).isEqualByComparingTo("299");
        });
        verify(repository).search("数码家电", "耳机", true);
    }

    @Test
    void treatsBlankFiltersAsAbsent() {
        when(repository.search(null, null, null)).thenReturn(List.of());

        ProductListResponse response = service.findProducts(" ", "", null);

        assertThat(response.items()).isEmpty();
        verify(repository).search(null, null, null);
    }
}
