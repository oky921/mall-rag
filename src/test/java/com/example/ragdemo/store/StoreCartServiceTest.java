package com.example.ragdemo.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StoreCartServiceTest {

    @Mock
    private StoreCartItemRepository cartRepository;

    @Mock
    private StoreSkuRepository skuRepository;

    @Mock
    private CurrentStoreUser currentUser;

    @InjectMocks
    private StoreCartService service;

    private StoreSku sku;

    @BeforeEach
    void setUp() {
        StoreProduct product = new StoreProduct("TEST-1", "测试商品", "副标题", "描述", "测试分类",
                new BigDecimal("100"), new BigDecimal("120"), 5, 0, new BigDecimal("4.8"),
                "https://example.com/product.jpg", true);
        ReflectionTestUtils.setField(product, "id", 1L);
        sku = new StoreSku(product, "TEST-1-BLACK", "{\"颜色\":\"黑色\"}",
                new BigDecimal("100"), new BigDecimal("120"), 5, 0,
                "https://example.com/product.jpg", true);
        ReflectionTestUtils.setField(sku, "id", 10L);
        when(currentUser.userId()).thenReturn(1L);
    }

    @Test
    void addsSkuToDatabaseCartAndReturnsTotals() {
        AtomicReference<StoreCartItem> savedItem = new AtomicReference<>();
        when(skuRepository.findByIdAndEnabledTrue(10L)).thenReturn(Optional.of(sku));
        when(cartRepository.findByUserIdAndSkuId(1L, 10L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(StoreCartItem.class))).thenAnswer(invocation -> {
            StoreCartItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 20L);
            savedItem.set(item);
            return item;
        });
        when(cartRepository.findByUserIdOrderByUpdatedAtDesc(1L))
                .thenAnswer(ignored -> List.of(savedItem.get()));

        StoreApiModels.CartResponse response = service.add(new StoreApiModels.AddCartItemRequest(10L, 2));

        assertThat(response.totalQuantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("200");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.skuId()).isEqualTo(10L);
            assertThat(item.specValues()).containsEntry("颜色", "黑色");
            assertThat(item.quantity()).isEqualTo(2);
        });
    }

    @Test
    void rejectsQuantityAboveSkuStock() {
        when(skuRepository.findByIdAndEnabledTrue(10L)).thenReturn(Optional.of(sku));
        when(cartRepository.findByUserIdAndSkuId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(new StoreApiModels.AddCartItemRequest(10L, 6)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
